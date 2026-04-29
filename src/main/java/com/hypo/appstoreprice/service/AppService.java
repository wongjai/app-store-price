package com.hypo.appstoreprice.service;

import cn.hutool.cache.Cache;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.mutable.Mutable;
import cn.hutool.core.lang.mutable.MutableObj;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.hypo.appstoreprice.common.BizException;
import com.hypo.appstoreprice.pojo.bean.Money;
import com.hypo.appstoreprice.pojo.enums.AreaEnum;
import com.hypo.appstoreprice.pojo.request.GetAppListReqDTO;
import com.hypo.appstoreprice.pojo.response.*;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * app service
 *
 * @author hypo
 * @date 2025-09-16
 */
@Slf4j
@Service
public class AppService {

    /**
     * app 搜尋列表快取
     */
    private static final Cache<String, List<GetAppListResDTO>> APP_LIST_CACHE = new TimedCache<>(Duration.ofDays(1L).toMillis(), new ConcurrentHashMap<>());

    /**
     * app 資訊快取
     */
    private static final Cache<String, List<GetAppInfoResDTO>> APP_INFO_CACHE = new TimedCache<>(Duration.ofDays(1L).toMillis(), new ConcurrentHashMap<>());

    /**
     * lock pool
     */
    private static final ConcurrentHashMap<String, Object> LOCK_POOL = new ConcurrentHashMap<>();

    /**
     * 熱門搜尋詞排行
     */
    private static final Multiset<String> POPULAR_SEARCH_WORD = HashMultiset.create();

    /**
     * get popular search word list
     *
     * @return {@link List }<{@link String }>
     */
    public List<String> getPopularSearchWordList() {
        return POPULAR_SEARCH_WORD.entrySet()
            .stream()
            .sorted(Comparator.comparingInt(Multiset.Entry<String>::getCount).reversed())
            .limit(10)
            .map(Multiset.Entry::getElement)
            .collect(Collectors.toList());
    }

    /**
     * get area list
     *
     * @return {@link List }<{@link AreaResDTO }>
     */
    public List<AreaResDTO> getAreaList() {
        return Arrays.stream(AreaEnum.values()).map(item -> {
            AreaResDTO resDTO = new AreaResDTO();
            resDTO.setCode(item.getCode());
            resDTO.setName(item.getName());
            return resDTO;
        }).collect(Collectors.toList());
    }

    /**
     * get app list
     *
     * @param reqDTO req dto
     * @return {@link List }<{@link GetAppListResDTO }>
     */
    public List<GetAppListResDTO> getAppList(GetAppListReqDTO reqDTO) {
        // 記錄搜尋次數
        POPULAR_SEARCH_WORD.add(reqDTO.getAppName());

        // 無鎖檢查快取
        String cacheKey = StrUtil.format("{}-{}", reqDTO.getAreaCode(), reqDTO.getAppName());
        List<GetAppListResDTO> appListCache = APP_LIST_CACHE.get(cacheKey);
        if (CollUtil.isNotEmpty(appListCache)) {
            return appListCache;
        }

        // 獲取鎖對象（細粒度鎖，按 appId 分段）
        Object lock = LOCK_POOL.computeIfAbsent(StrUtil.format("getAppList-{}-{}", reqDTO.getAreaCode(), reqDTO.getAppName()), k -> new Object());

        synchronized (lock) {
            // 鎖內再次檢查快取（雙重檢查）
            appListCache = APP_LIST_CACHE.get(cacheKey);
            if (CollUtil.isNotEmpty(appListCache)) {
                return appListCache;
            }
            List<GetAppListResDTO> resultList = new CopyOnWriteArrayList<>();
            CollUtil.newArrayList("iphone", "ipad", "mac", "tv").parallelStream().forEach(entity -> {
                String searchUrl = StrUtil.format("https://apps.apple.com/{}/{}/search?term={}", reqDTO.getAreaCode(), entity, StrUtil.trim(reqDTO.getAppName()));
                HttpResponse response = HttpUtil.createGet(searchUrl).execute();
                if (response.getStatus() != HttpStatus.OK.value()) {
                    String errorMessage = StrUtil.format("search failed, areaCode: {}, appName: {}", reqDTO.getAreaCode(), reqDTO.getAppName());
                    log.error(errorMessage);
                    throw new BizException(errorMessage);
                }
                // 解析 HTML
                Document doc = Jsoup.parse(response.body());
                // 構造輸出參數
                Element scriptElement = doc.selectFirst("#serialized-server-data");
                if (Objects.isNull(scriptElement)) {
                    log.error("scriptElement is null, areaCode: {}, appName: {}", reqDTO.getAreaCode(), reqDTO.getAppName());
                    return;
                }
                JSONArray items = JSON.parseObject(scriptElement.html().trim()).getJSONArray("data").getJSONObject(0).getJSONObject("data")
                    .getJSONArray("shelves").getJSONObject(0)
                    .getJSONArray("items");
                JSONArray data = new JSONArray();
                CollUtil.forEach(items, (item, index) -> {
                    JSONObject lockup = items.getJSONObject(index).getJSONObject("lockup");
                    if (Objects.nonNull(lockup) && !"bundle".equals(items.getJSONObject(index).getString("resultType"))) {
                        data.add(lockup);
                    }
                });
                List<AppStoreSearchResultDTO> entitySearchResultList = data.toList(AppStoreSearchResultDTO.class);
                List<GetAppListResDTO> entityResultList = entitySearchResultList.stream().map(item -> {
                    GetAppListResDTO dto = new GetAppListResDTO();
                    dto.setAppId(item.getAdamId());
                    dto.setAppName(item.getTitle());
                    dto.setAppImage(item.getIcon().getTemplate());
                    dto.setAppDesc(item.getSubtitle());
                    return dto;
                }).toList();
                resultList.addAll(entityResultList);
            });
            APP_LIST_CACHE.put(cacheKey, resultList.stream().collect(Collectors.toMap(
                    GetAppListResDTO::getAppId,
                    Function.identity(),
                    (existingValue, newValue) -> existingValue,
                    LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparingInt(item -> {
                    if (StrUtil.equalsIgnoreCase(item.getAppName(), reqDTO.getAppName())) {
                        return 0;
                    }
                    if (StrUtil.startWithAnyIgnoreCase(item.getAppName(), reqDTO.getAppName())) {
                        return 1;
                    }
                    if (StrUtil.containsAll(item.getAppName().toLowerCase(), reqDTO.getAppName().toLowerCase().split(StrUtil.EMPTY))) {
                        return 2;
                    }
                    return 3;
                })).toList());
        }
        return APP_LIST_CACHE.get(cacheKey);
    }

    /**
     * get app info
     *
     * @param appId app id
     * @return {@link GetAppInfoResDTO }
     */
    public List<GetAppInfoResDTO> getAppInfo(String appId) {
        // 無鎖檢查快取
        List<GetAppInfoResDTO> appInfoListCache = APP_INFO_CACHE.get(appId);
        if (CollUtil.isNotEmpty(appInfoListCache)) {
            return appInfoListCache;
        }

        // 獲取鎖對象（細粒度鎖，按 appId 分段）
        Object lock = LOCK_POOL.computeIfAbsent(StrUtil.format("getAppInfo-{}", appId), k -> new Object());

        synchronized (lock) {
            // 鎖內再次檢查快取（雙重檢查）
            appInfoListCache = APP_INFO_CACHE.get(appId);
            if (CollUtil.isNotEmpty(appInfoListCache)) {
                return appInfoListCache;
            }
            Mutable<List<GetAppInfoResDTO>> resultList = new MutableObj<>(new CopyOnWriteArrayList<>());
            Arrays.stream(AreaEnum.values()).parallel().forEach(areaEnum -> {
                String appStoreUrl = StrUtil.format("https://apps.apple.com/{}/app/id{}", areaEnum.getCode(), appId);
                HttpResponse response = HttpUtil.createGet(appStoreUrl, true).execute();
                if (response.getStatus() != HttpStatus.OK.value()) {
                    log.error("appId: {}, app not found in {} app store", appId, areaEnum.getCode());
                    log.error("{}-{}:failed", appId, areaEnum.getCode());
                    return;
                } else {
                    log.info("{}-{}:success", appId, areaEnum.getCode());
                }
                // 解析 HTML
                Document doc = Jsoup.parse(response.body());
                // 構造輸出參數
                GetAppInfoResDTO resDTO = new GetAppInfoResDTO();
                resDTO.setAppId(appId);
                resDTO.setArea(areaEnum.getCode());
                resDTO.setAreaName(areaEnum.getName());
                Element scriptElement = doc.selectFirst("#serialized-server-data");
                if (Objects.isNull(scriptElement)) {
                    log.error("appId: {}, area: {}, script element not found", appId, areaEnum.getCode());
                    return;
                }
                JSONObject jsonResult = JSON.parseObject(scriptElement.html().trim()).getJSONArray("data").getJSONObject(0).getJSONObject("data");
                // 提取應用名稱
                resDTO.setName(jsonResult.getString("title"));
                // 提取副標題
                resDTO.setSubtitle(jsonResult.getJSONObject("lockup").getString("subtitle"));
                // 提取開發者資訊
                resDTO.setDeveloper(jsonResult.getJSONObject("developerAction").getString("title"));
                resDTO.setAppStoreUrl(appStoreUrl);
                // 提取價格資訊
                resDTO.setPrice(parsePrice(jsonResult.getJSONObject("lockup").getJSONObject("offerDisplayProperties").getString("priceFormatted"), areaEnum));
                // 查找所有內購列表項
                JSONArray inAppPurchaseArray = jsonResult.getJSONObject("shelfMapping").getJSONObject("information").getJSONArray("items")
                    .toList(JSONObject.class)
                    .stream()
                    .filter(item -> areaEnum.getInAppPurchaseStr().equals(item.getString("title")))
                    .findFirst()
                    .map(item -> item.getJSONArray("items"))
                    .map(item -> item.getJSONObject(0))
                    .map(item -> item.getJSONArray("textPairs"))
                    .orElse(new JSONArray());
                // 儲存解析結果
                List<InAppPurchaseDTO> inAppPurchaseList = new ArrayList<>();
                for (int i = 0; i < inAppPurchaseArray.size(); i++) {
                    JSONArray jsonArray = inAppPurchaseArray.getJSONArray(i);
                    InAppPurchaseDTO purchaseDTO = new InAppPurchaseDTO();
                    purchaseDTO.setObject(jsonArray.getString(0));
                    purchaseDTO.setPrice(parsePrice(jsonArray.getString(1), areaEnum));
                    inAppPurchaseList.add(purchaseDTO);
                }
                resDTO.setInAppPurchaseList(inAppPurchaseList);
                resultList.get().add(resDTO);
            });
            // 有售價的應用按價格升序，有內購的應用按內購價格升序
            resultList.set(resultList.get().stream()
                .sorted(Comparator.comparing(item -> item.getPrice().getHkdPrice()))
                .sorted(Comparator.comparing(
                    item -> item.getInAppPurchaseList().stream()
                        .min(Comparator.comparing(ele -> ele.getPrice().getHkdPrice()))
                        .orElse(InAppPurchaseDTO.none())
                        .getPrice().getHkdPrice()))
                .collect(Collectors.toList()));
            APP_INFO_CACHE.put(appId, resultList.get());
        }
        return APP_INFO_CACHE.get(appId);
    }

    /**
     * parse price
     *
     * @param priceStr price str
     * @param areaEnum area enum
     * @return {@link Money }
     */
    private Money parsePrice(String priceStr, AreaEnum areaEnum) {
        if (StrUtil.isBlank(priceStr)) {
            return new Money(areaEnum.getCurrencyCode(), BigDecimal.ZERO);
        }
        priceStr = priceStr.replace("ribu", "000");
        priceStr = priceStr.replace(areaEnum.getThousandsSeparator(), StrUtil.EMPTY);
        if (StrUtil.DOT.equals(areaEnum.getThousandsSeparator())) {
            priceStr = priceStr.replace(StrUtil.COMMA, StrUtil.DOT);
        }
        priceStr = ReUtil.get("\\d+(\\.\\d+)?", priceStr, 0);
        return new Money(areaEnum.getCurrencyCode(), new BigDecimal(priceStr).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * get app info comparison
     *
     * @param appId app id
     * @return {@link List }<{@link GetAppInfoComparisonResDTO }>
     */
    public List<GetAppInfoComparisonResDTO> getAppInfoComparison(String appId) {
        List<GetAppInfoResDTO> appInfoList = this.getAppInfo(appId);
        if (CollUtil.isEmpty(appInfoList)) {
            return new ArrayList<>();
        }

        // 用於儲存比較結果，key 為購買項目名稱
        Map<String, List<Money>> comparisonMap = new LinkedHashMap<>();

        // 收集各地區的 App 本體價格
        List<Money> appPriceList = appInfoList.stream()
            .map(GetAppInfoResDTO::getPrice)
            .collect(Collectors.toList());
        if (CollUtil.isNotEmpty(appPriceList)) {
            comparisonMap.put("軟件本體", appPriceList);
        }

        // 收集各地區的內購價格，按內購項目名稱分組
        // 同一地區可能存在相同名稱的內購項目，先按價格升序排序後再編序號區分
        for (GetAppInfoResDTO appInfo : appInfoList) {
            if (CollUtil.isEmpty(appInfo.getInAppPurchaseList())) {
                continue;
            }
            // 先去重（同名同價視為重複），再按價格升序排序
            List<InAppPurchaseDTO> sortedPurchaseList = appInfo.getInAppPurchaseList().stream()
                .collect(Collectors.collectingAndThen(
                    Collectors.toMap(
                        item -> item.getObject() + "||" + item.getPrice().getPrice().toPlainString(),
                        item -> item,
                        (a, b) -> a,
                        java.util.LinkedHashMap::new
                    ),
                    map -> new ArrayList<>(map.values())
                ))
                .stream()
                .sorted(Comparator.comparing(item -> item.getPrice().getHkdPrice()))
                .toList();
            // 記錄當前地區每個內購項目名稱出現的次數
            Map<String, Integer> objectCountMap = new HashMap<>();
            for (InAppPurchaseDTO purchase : sortedPurchaseList) {
                String objectName = purchase.getObject();
                int count = objectCountMap.getOrDefault(objectName, 0) + 1;
                objectCountMap.put(objectName, count);
                // 如果同名項目出現多次，使用「名稱 #序號」格式區分
                String key = count > 1 ? StrUtil.format("{} #{}", objectName, count) : objectName;
                comparisonMap.computeIfAbsent(key, k -> new ArrayList<>()).add(purchase.getPrice());
            }
        }

        // 對每個內購項目的價格列表按港幣價格升序排序，並構建結果
        // 最終按價格列表數量降序排序（更多地區有售的項目排在前面）
        return comparisonMap.entrySet().stream()
            .map(entry -> {
                GetAppInfoComparisonResDTO resDTO = new GetAppInfoComparisonResDTO();
                resDTO.setObject(entry.getKey());
                resDTO.setPriceList(entry.getValue().stream()
                    .sorted(Comparator.comparing(Money::getHkdPrice))
                    .collect(Collectors.toList()));
                return resDTO;
            })
            .sorted(Comparator.comparing((GetAppInfoComparisonResDTO item) -> item.getPriceList().size()).reversed())
            .collect(Collectors.toList());
    }

}
