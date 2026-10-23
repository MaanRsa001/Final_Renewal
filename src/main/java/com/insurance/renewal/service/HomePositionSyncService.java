package com.insurance.renewal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.renewal.dto.HomePositionMasterDTO;
import com.insurance.renewal.dto.HomePositionSyncResultDTO;
import com.insurance.renewal.entity.EwayMotorMakeModelMaster;
import com.insurance.renewal.entity.HomePositionMaster;
import com.insurance.renewal.entity.MotorDataDetails;
import com.insurance.renewal.entity.MotorDriverDetails;
import com.insurance.renewal.entity.PersonalInfo;
import com.insurance.renewal.entity.PolicyMasterRecord;
import com.insurance.renewal.repository.PolicyMasterRecordRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads policies from home_position_master (PRODUCT_ID = 5, STATUS = 'P') whose EXPIRY_DATE falls
 * between <b>today and today + 30 days</b>, enriches them and inserts them into <b>policy_master only</b>
 * (nothing is written to mst_customer / mst_product / mst_branch / mst_agent).
 *
 * <ul>
 *   <li>{@link HomePositionMaster}          - the policy rows (latest endorsement per policy)</li>
 *   <li>{@link MotorDataDetails}            - sum insured, reg no, mfg year, make / model / body type / usage, policy type</li>
 *   <li>{@link MotorDriverDetails}          - DRIVER_DOB</li>
 *   <li>{@link EwayMotorMakeModelMaster}    - VEH_MANF_COUNTRY (+ every other column as JSON)</li>
 *   <li>{@link PersonalInfo}                - mobile, email, first name, ID type / number, policy holder type</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomePositionSyncService {

    /** PRODUCT_ID on home_position_master. */
    private static final int HOME_PRODUCT_ID = 5;

    @PersistenceContext
    private EntityManager em;

    private final ObjectMapper objectMapper;
    private final PolicyMasterRecordRepository policyRecordRepo;
    private final RenewalBatchService batchService;   // net loading / discount -> final premium after the insert

    // ---- EwayCommonApi premium-calc (called while building the upcoming-expiry list) ----
    @Value("${eway.common.api.base-url}")
    private String ewayBaseUrl;
    @Value("${eway.common.api.login-id}")
    private String ewayLoginId;
    @Value("${eway.common.api.password}")
    private String ewayPassword;
    @Value("${eway.common.api.relogin-key}")
    private String ewayReLoginKey;
    /**
     * Where PremiumIncludedTax / PremiumIncludedTaxLC come from:
     * POLICY = the policy's own stored premium incl. tax (home_position_master OVERALL_PREMIUM_FC / _LC), no API call;
     * CALC   = recalculated by EwayCommonApi /common/premium-calc (sum of the selected covers).
     */
    @Value("${eway.premium.source:CALC}")
    private String premiumSource;

    private static final DateTimeFormatter EWAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final RestTemplate ewayRestTemplate = new RestTemplate();

    /** make / model / company keys read from motor_data_details, needed for the make-model lookup. */
    private record VehicleKey(String makeId, String modelId, String companyId) { }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Fetch + enrich only (no writes). Used by the GET preview endpoint. */
    // No @Transactional on purpose: the enrich* helpers catch and log query failures. Inside one transaction a failed
    // query marks it rollback-only, so the commit then throws UnexpectedRollbackException. Without it each query
    // runs on its own and a failed lookup only leaves that part of the row null (see the WARN log).
    public List<HomePositionMasterDTO> fetchUpcomingExpiry() {
        List<HomePositionMasterDTO> result = new ArrayList<>();
        List<HomePositionMaster> policies = findLatestExpiringPolicies();
        boolean useCalc = "CALC".equalsIgnoreCase(premiumSource);
        Map<String, Object> sectionCoverCache = new LinkedHashMap<>();   // /master/SectionCoverDetails per company+product
        String ewayToken = (useCalc && !policies.isEmpty()) ? fetchEwayTokenSafe() : null;   // one login for the whole list

        for (HomePositionMaster h : policies) {
            HomePositionMasterDTO row = toDto(h);
            enrichWithAgentName(row);
            enrichWithPersonalInfo(row);
            VehicleKey key = enrichWithMotorData(row);
            enrichWithDriverDob(row);
            enrichWithMakeModel(row, key);
            if (useCalc) {
                enrichWithPremiumCalc(row, ewayToken, sectionCoverCache);
            } else {
                usePolicyPremium(row);
            }
            result.add(row);
        }
        return result;
    }

    /**
     * Inserts the upcoming-expiry policies into policy_master (skips POLICY_NOs already present).
     * Not @Transactional on purpose: every save() commits on its own, so one bad row is reported in
     * "errors" instead of rolling back the whole batch.
     */
    public HomePositionSyncResultDTO syncToPolicyMaster() {
        List<HomePositionMasterDTO> rows = fetchUpcomingExpiry();

        int inserted = 0;
        int alreadyExists = 0;
        List<String> errors = new ArrayList<>();

        for (HomePositionMasterDTO row : rows) {
            // policy_master.policy_no must hold the ORIGINAL policy number (no endorsement suffix such as "-5").
            // e.g. POLICY_NO "P11/2026/100/1002/10/1005477-5" -> "P11/2026/100/1002/10/1005477"
            String policyNo = resolvePolicyNoForInsert(row);
            try {
                if (policyNo == null) {
                    errors.add("Skipped row with null POLICY_NO (CUSTOMER_ID=" + row.getCustomerId() + ")");
                    continue;
                }
                if (policyRecordRepo.existsByPolicyNo(policyNo)) {
                    alreadyExists++;
                    continue;
                }
                PolicyMasterRecord saved = policyRecordRepo.save(toPolicyMasterRecord(row, policyNo));
                inserted++;
                // calculate net loading (+%) / discount (-%) with the renewal rules and store
                // final_premium_lc / final_premium_fc = overall premium x (1 + net% / 100) on the new policy
                try {
                    batchService.reprocessPolicy(saved, LocalDate.now());
                } catch (Exception ex) {
                    log.warn("Inserted {} but loading/discount was not calculated: {}", policyNo, ex.getMessage());
                    errors.add(policyNo + ": inserted, but net loading/discount not calculated - " + ex.getMessage());
                }
            } catch (Exception ex) {
                log.error("Failed to sync POLICY_NO={} (source POLICY_NO={}) from home_position_master",
                        policyNo, row.getPolicyNo(), ex);
                errors.add(policyNo + ": " + ex.getMessage());
            }
        }

        return HomePositionSyncResultDTO.builder()
                .totalFetched(rows.size())
                .inserted(inserted)
                .alreadyExists(alreadyExists)
                .failed(errors.size())
                .errors(errors)
                .build();
    }

    // -----------------------------------------------------------------------
    // Criteria query 1 - home_position_master
    // -----------------------------------------------------------------------

    /**
     * Latest endorsement per policy family, expiring from today up to and including day +30.
     *
     * <pre>
     * policy family = ORIGINAL_POLICY_NO, or POLICY_NO itself when ORIGINAL_POLICY_NO is blank
     * latest        = row whose ENDT_COUNT = MAX(ENDT_COUNT) of its family   (correlated subquery)
     * window        = EXPIRY_DATE &gt;= today 00:00  AND  EXPIRY_DATE &lt; (today + 31 days) 00:00
     * </pre>
     * EXPIRY_DATE is a datetime, so the upper bound is "&lt; today + 31 days" to keep the whole of day 30.
     */
    private List<HomePositionMaster> findLatestExpiringPolicies() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<HomePositionMaster> cq = cb.createQuery(HomePositionMaster.class);
        Root<HomePositionMaster> h = cq.from(HomePositionMaster.class);

        // MAX(ENDT_COUNT) of the same policy family (same filters as the outer query)
        Subquery<Integer> maxEndt = cq.subquery(Integer.class);
        Root<HomePositionMaster> h2 = maxEndt.from(HomePositionMaster.class);
        maxEndt.select(cb.max(cb.coalesce(h2.<Integer>get("endtCount"), 0)))
               .where(baseFilter(cb, h2),
                      cb.equal(policyFamily(cb, h2), policyFamily(cb, h)));

        LocalDateTime windowStart = LocalDate.now().atStartOfDay();
        LocalDateTime windowEndExclusive = windowStart.plusDays(31);

        cq.select(h)
          .where(baseFilter(cb, h),
                 cb.equal(cb.coalesce(h.<Integer>get("endtCount"), 0), maxEndt),
                 cb.greaterThanOrEqualTo(h.<LocalDateTime>get("expiryDate"), windowStart),
                 cb.lessThan(h.<LocalDateTime>get("expiryDate"), windowEndExclusive))
          .orderBy(cb.asc(h.get("expiryDate")), cb.desc(h.get("homeSno")));

        List<HomePositionMaster> candidates = em.createQuery(cq).getResultList();

        // home_position_master keeps one row per vehicle, so two rows of a family can share the same
        // ENDT_COUNT. Keep exactly one per family: the one with the highest HOME_SNO.
        Map<String, HomePositionMaster> onePerFamily = new LinkedHashMap<>();
        for (HomePositionMaster c : candidates) {
            String family = isBlank(c.getOriginalPolicyNo()) ? c.getPolicyNo() : c.getOriginalPolicyNo();
            HomePositionMaster existing = onePerFamily.get(family);
            if (existing == null || c.getHomeSno() > existing.getHomeSno()) {
                onePerFamily.put(family, c);
            }
        }
        return new ArrayList<>(onePerFamily.values());
    }

    /** PRODUCT_ID = 5 AND STATUS = 'P' AND POLICY_NO is not null / blank. */
    private Predicate baseFilter(CriteriaBuilder cb, Root<HomePositionMaster> r) {
        return cb.and(
                cb.equal(r.<Integer>get("productId"), HOME_PRODUCT_ID),
                cb.equal(r.<String>get("status"), "P"),
                cb.isNotNull(r.<String>get("policyNo")),
                cb.notEqual(r.<String>get("policyNo"), ""));
    }

    /** COALESCE(NULLIF(ORIGINAL_POLICY_NO, ''), POLICY_NO) */
    private Expression<String> policyFamily(CriteriaBuilder cb, Root<HomePositionMaster> r) {
        return cb.coalesce(
                cb.nullif(r.<String>get("originalPolicyNo"), ""),
                r.<String>get("policyNo"));
    }

    private HomePositionMasterDTO toDto(HomePositionMaster h) {
        return HomePositionMasterDTO.builder()
                .policyNo(h.getPolicyNo())
                .originalPolicyNo(isBlank(h.getOriginalPolicyNo()) ? null : h.getOriginalPolicyNo())  // ORIGINAL_POLICY_NO
                .productId(h.getProductId())                                   // PRODUCT_ID
                .customerId(h.getCustomerId())                                 // CUSTOMER_ID
                .branchId(h.getBranchCode())                                   // BRANCH_CODE
                .agentId(h.getAgencyCode() == null ? null : String.valueOf(h.getAgencyCode()))   // AGENCY_CODE
                .policyStartDate(h.getInceptionDate() == null ? null : h.getInceptionDate().toLocalDate())  // INCEPTION_DATE
                .policyExpiryDate(h.getExpiryDate() == null ? null : h.getExpiryDate().toLocalDate())       // EXPIRY_DATE
                .policyStatus(h.getStatus())                                   // STATUS
                .companyId(parseIntSafe(h.getCompanyId()))                     // COMPANY_ID
                .productName(h.getProductName())                               // PRODUCT_NAME
                .customerName(h.getCustomerName())                             // CUSTOMER_NAME
                .branchName(h.getBranchName())                                 // BRANCH_NAME
                .sectionId(h.getSectionId())                                   // SECTION_ID
                .currency(h.getCurrency())                                     // CURRENCY
                .premiumLc(toBigDecimal(h.getPremiumLc()))                     // PREMIUM_LC
                .premiumFc(toBigDecimal(h.getPremiumFc()))                     // PREMIUM_FC
                .overallPremiumFc(toBigDecimal(h.getOverallPremiumFc()))       // OVERALL_PREMIUM_FC
                .overallPremiumLc(toBigDecimal(h.getOverallPremiumLc()))       // OVERALL_PREMIUM_LC
                .sourceType(h.getSourceType())                                 // SOURCE_TYPE
                .quoteNo(h.getQuoteNo())                                       // QUOTE_NO
                .endtCount(h.getEndtCount())                                   // ENDT_COUNT
                .applicationId(h.getApplicationId())                           // APPLICATION_ID
                .loginId(h.getLoginId())                                       // LOGIN_ID
                .brokerCode(h.getBrokerCode())                                 // BROKER_CODE
                .accountExecutiveId(h.getAcExecutiveId())                      // AC_EXECUTIVE_ID
                .policyTerm(h.getPolicyTerm())                                 // POLICY_TERM
                .noClaimDiscount(toBigDecimal(h.getNoClaimDiscount()))         // NO_CLAIM_DISCOUNT
                .build();
    }

    // -----------------------------------------------------------------------
    // EwayCommonApi - POST /common/premium-calc  ->  PremiumIncludedTax / PremiumIncludedTaxLC
    // -----------------------------------------------------------------------

    /**
     * Calls EwayCommonApi {@code /common/premium-calc} for the renewal period (day after expiry, one year)
     * and stores the total tax-inclusive premium on the row as PremiumIncludedTax / PremiumIncludedTaxLC.
     * Never throws: if the call fails the two fields stay null and a WARN is logged.
     */
    /** premium-calc request for the renewal period (day after expiry, one year) from the policy row. */
    private Map<String, Object> buildCalcRequest(HomePositionMasterDTO row) {
        LocalDate effective = row.getPolicyExpiryDate().plusDays(1);
        LocalDate end = effective.plusYears(1).minusDays(1);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("InsuranceId", String.valueOf(row.getCompanyId()));
        req.put("BranchCode", row.getBranchId());
        req.put("SectionId", row.getSectionId() == null ? null : String.valueOf(row.getSectionId()));
        req.put("ProductId", row.getProductId() == null ? null : String.valueOf(row.getProductId()));
        req.put("VehicleId", row.getVehicleId());
        req.put("LocationId", row.getLocationId() == null ? "1" : String.valueOf(row.getLocationId()));
        req.put("EffectiveDate", effective.format(EWAY_DATE));
        req.put("PolicyEndDate", end.format(EWAY_DATE));
        req.put("InsuranceClass", row.getInsuranceType());
        req.put("BodyType", row.getBodytypeId());
        req.put("MotorUsage", row.getVehicleusageId());
        req.put("ManufactureYear", row.getVehicleMfgDate() == null ? null
                : String.valueOf(row.getVehicleMfgDate().getYear()));
        req.put("ClaimYn", "N");
        req.put("PeriodOfInsurance", String.valueOf(ChronoUnit.DAYS.between(effective, end.plusDays(1))));
        req.put("SumInsured", plain(row.getSumInsured()));
        req.put("TpdIncreaseLimit", "0");
        req.put("WindScreenSumInsured", plain(row.getWindScreenSumInsured()));
        req.put("AcccessoriesSumInsured", plain(row.getAccessoriesSumInsured()));
        req.put("FuelType", row.getFuelType());
        return req;
    }

    /**
     * Recalculation: calls premium-calc for one policy_master record and returns the raw CoverList
     * (CoverId, CoverageType, isSelected, UserOpt, PremiumIncludedTax, PremiumIncludedTaxLC ...).
     * Throws IllegalStateException with a readable message when the call cannot be made.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchCoverListForPolicy(PolicyMasterRecord p) {
        if (p.getPolicyExpiryDate() == null || p.getCompanyId() == null)
            throw new IllegalStateException("Policy expiry date or company id is missing");
        HomePositionMasterDTO row = HomePositionMasterDTO.builder()
                .policyNo(p.getPolicyNo())
                .policyExpiryDate(p.getPolicyExpiryDate())
                .companyId(p.getCompanyId())
                .branchId(p.getBranchId() == null ? null : String.valueOf(p.getBranchId()))
                .sectionId(p.getSectionId())
                .productId(p.getProductId())
                .vehicleId(p.getVehicleId())
                .locationId(p.getLocationId())
                .insuranceType(p.getInsuranceType())
                .bodytypeId(p.getBodytypeId() == null ? null : String.valueOf(p.getBodytypeId()))
                .vehicleusageId(p.getVehicleusageId() == null ? null : String.valueOf(p.getVehicleusageId()))
                .vehicleMfgDate(p.getVehicleMfgDate())
                .sumInsured(p.getSumInsured())
                .windScreenSumInsured(p.getWindScreenSumInsured())
                .accessoriesSumInsured(p.getAccessoriesSumInsured())
                .fuelType(p.getFuelType())
                .vdRefNo(p.getVdRefNo())
                .build();
        String token = fetchEwayTokenSafe();
        if (isBlank(token))
            throw new IllegalStateException("No token from " + ewayBaseUrl + "/authentication/login - check eway.common.api.* properties");
        Map<String, Object> req = buildCalcRequest(row);
        applyOldRecordInputs(req, row, token, new LinkedHashMap<>());
        log.info("recalculate premium-calc request for POLICY_NO={} -> {}", p.getPolicyNo(), req);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);
        Map<String, Object> res = ewayRestTemplate.postForObject(
                ewayBaseUrl + "/common/premium-calc", new HttpEntity<>(req, headers), Map.class);
        if (res == null || !(res.get("CoverList") instanceof List<?> covers))
            throw new IllegalStateException("premium-calc returned no CoverList. Response: " + res);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : covers) out.add((Map<String, Object>) o);
        return out;
    }

    @SuppressWarnings("unchecked")
    private void enrichWithPremiumCalc(HomePositionMasterDTO row, String token, Map<String, Object> sectionCoverCache) {
        try {
            if (row.getPolicyExpiryDate() == null || row.getCompanyId() == null) {
                row.setPremiumCalcMessage("Skipped: policy expiry date or company id is missing");
                return;
            }

            Map<String, Object> req = buildCalcRequest(row);

            if (isBlank(token)) {
                log.warn("premium-calc skipped for POLICY_NO={}: no EwayCommonApi token (check eway.common.api.* properties)",
                        row.getPolicyNo());
                row.setPremiumCalcMessage("No token from " + ewayBaseUrl + "/authentication/login - check "
                        + "eway.common.api.base-url / login-id / password (see WARN 'EwayCommonApi login')");
                return;
            }
            applyOldRecordInputs(req, row, token, sectionCoverCache);
            log.info("premium-calc request for POLICY_NO={} -> {}/common/premium-calc {}", row.getPolicyNo(), ewayBaseUrl, req);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + token);

            Map<String, Object> res = ewayRestTemplate.postForObject(
                    ewayBaseUrl + "/common/premium-calc", new HttpEntity<>(req, headers), Map.class);
            if (res == null || !(res.get("CoverList") instanceof List<?> covers)) {
                log.warn("premium-calc returned no CoverList for POLICY_NO={} (empty body means EwayCommonApi hit an error - "
                        + "see its console). Response: {}", row.getPolicyNo(), res);
                row.setPremiumCalcMessage("premium-calc returned no CoverList (company " + row.getCompanyId()
                        + ", section " + row.getSectionId() + "). Check the premium-calc console: usually missing "
                        + "product_section_master / section_cover_master data. Response: " + res);
                return;
            }

            BigDecimal tax = BigDecimal.ZERO;
            BigDecimal taxLc = BigDecimal.ZERO;
            for (Object o : covers) {
                Map<String, Object> c = (Map<String, Object>) o;
                // same cover rules as the Recalculate API: B always; O+UserOpt Y+isSelected N; O+isSelected Y+UserOpt null
                if (!RenewalRecalculationService.countsTowardsOverall(c)) continue;
                tax = tax.add(num(c.get("PremiumIncludedTax")));
                taxLc = taxLc.add(num(c.get("PremiumIncludedTaxLC")));
            }
            row.setPremiumIncludedTax(tax.setScale(2, java.math.RoundingMode.HALF_UP));
            row.setPremiumIncludedTaxLC(taxLc.setScale(2, java.math.RoundingMode.HALF_UP));
            row.setPremiumCalcMessage("OK");
            log.info("premium-calc result for POLICY_NO={}: PremiumIncludedTax={} PremiumIncludedTaxLC={}",
                    row.getPolicyNo(), tax, taxLc);
        } catch (Exception ex) {
            log.warn("premium-calc failed for POLICY_NO={}: {}", row.getPolicyNo(), ex.getMessage());
            row.setPremiumCalcMessage("premium-calc call failed: " + ex.getMessage());
        }
    }

    /** PremiumIncludedTax(LC) = the policy's stored premium incl. tax (OVERALL_PREMIUM_FC / _LC). */
    private void usePolicyPremium(HomePositionMasterDTO row) {
        BigDecimal fc = row.getOverallPremiumFc();
        BigDecimal lc = row.getOverallPremiumLc();
        row.setPremiumIncludedTax(fc != null ? fc : lc);
        row.setPremiumIncludedTaxLC(lc != null ? lc : fc);
        row.setPremiumCalcMessage(fc == null && lc == null
                ? "Policy has no OVERALL_PREMIUM stored" : "OK (policy stored premium incl. tax)");
    }

    // -----------------------------------------------------------------------
    // premium-calc inputs taken from the OLD record (ms_vehicle_details)
    // -----------------------------------------------------------------------

    /**
     * Fills the premium-calc request from the policy's old record:
     * <ol>
     *   <li>sum insured (common to every cover) and motor usage always come from ms_vehicle_details;</li>
     *   <li>every other rating input of the policy's section is found through EwayCommonApi
     *       {@code POST /master/SectionCoverDetails} (RatingFields: InputTableName = entity, InputColumnName = column,
     *       JsonKey = key to send) and its value is read from the old record.</li>
     * </ol>
     * Percentage covers (CalcType P) are then priced by premium-calc itself: sum insured x cover percentage.
     * Never throws; anything not found keeps the value already in the request.
     */
    @SuppressWarnings("unchecked")
    private void applyOldRecordInputs(Map<String, Object> req, HomePositionMasterDTO row, String token,
                                      Map<String, Object> sectionCoverCache) {
        try {
            Map<String, Object> rec = loadOldVehicleRecord(row);
            if (rec.isEmpty()) {
                log.info("No ms_vehicle_details record for VD_REFNO={} (POLICY_NO={}); using motor_data_details values",
                        row.getVdRefNo(), row.getPolicyNo());
                return;
            }

            // common inputs
            putIfPresent(req, "SumInsured", rec.get("SUM_INSURED"));
            putIfPresent(req, "MotorUsage", rec.get("MOTOR_USAGE"));

            // rating inputs of this section: entity + column -> value from the old record
            for (Map<String, Object> f : fetchRatingFields(row, token, sectionCoverCache)) {
                String table = String.valueOf(f.get("InputTableName"));
                String column = String.valueOf(f.get("InputColumnName"));
                if (isBlank(column)) continue;
                // JsonKey is null in SectionCoverDetails -> derive it from the column: motorUsage -> MotorUsage, classType -> Class
                String jsonKey = f.get("JsonKey") == null ? null : f.get("JsonKey").toString();
                if (isBlank(jsonKey)) {
                    jsonKey = "classType".equalsIgnoreCase(column) ? "Class"
                            : Character.toUpperCase(column.charAt(0)) + column.substring(1);
                }
                // renewal period (days) is calculated from the new dates, not copied from the old policy
                if ("PeriodOfInsurance".equalsIgnoreCase(jsonKey)) continue;
                if (!"msvehicledetails".equals(table.replace("_", "").toLowerCase())) continue;   // other entities not handled
                putIfPresent(req, jsonKey, rec.get(camelToUpperSnake(column)));
            }
        } catch (Exception ex) {
            log.warn("Could not apply old-record inputs for POLICY_NO={}: {}", row.getPolicyNo(), ex.getMessage());
        }
    }

    /** Latest ms_vehicle_details row of the policy (VD_REFNO from motor_data_details), keys upper-cased. */
    private Map<String, Object> loadOldVehicleRecord(HomePositionMasterDTO row) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (row.getVdRefNo() == null) return out;
        List<Tuple> rows = em.createNativeQuery(
                        "SELECT * FROM ms_vehicle_details WHERE VD_REFNO = ?1 ORDER BY ENTRY_DATE DESC", Tuple.class)
                .setParameter(1, row.getVdRefNo())
                .setMaxResults(1)
                .getResultList();
        if (rows.isEmpty()) return out;
        Tuple t = rows.get(0);
        for (TupleElement<?> e : t.getElements()) {
            out.put(e.getAlias().toUpperCase(), t.get(e.getAlias()));
        }
        return out;
    }

    /** RatingFields (all covers) of the policy's section, from POST {base}/master/SectionCoverDetails (cached). */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchRatingFields(HomePositionMasterDTO row, String token,
                                                         Map<String, Object> cache) {
        List<Map<String, Object>> fields = new ArrayList<>();
        String key = row.getCompanyId() + "|" + row.getProductId();
        Map<String, Object> res = (Map<String, Object>) cache.get(key);
        if (res == null && !cache.containsKey(key)) {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("InsuranceId", String.valueOf(row.getCompanyId()));
                body.put("ProductId", String.valueOf(row.getProductId()));
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + token);
                res = ewayRestTemplate.postForObject(ewayBaseUrl + "/master/SectionCoverDetails",
                        new HttpEntity<>(body, headers), Map.class);
            } catch (Exception ex) {
                log.warn("SectionCoverDetails failed for {}: {}", key, ex.getMessage());
            }
            cache.put(key, res);
        }
        if (res == null || !(res.get("SectionList") instanceof List<?> sections)) return fields;

        for (Object so : sections) {
            Map<String, Object> section = (Map<String, Object>) so;
            if (!String.valueOf(row.getSectionId()).equals(String.valueOf(section.get("SectionId")))) continue;
            if (!(section.get("CoverList") instanceof List<?> covers)) continue;
            for (Object co : covers) {
                Map<String, Object> cover = (Map<String, Object>) co;
                if (!(cover.get("FactorDetails") instanceof Map<?, ?> fd)) continue;
                if (!(fd.get("RatingFields") instanceof List<?> rfs)) continue;
                for (Object rf : rfs) fields.add((Map<String, Object>) rf);
            }
        }
        return fields;
    }

    private static void putIfPresent(Map<String, Object> req, String key, Object value) {
        if (value == null || String.valueOf(value).isBlank()) return;
        req.put(key, value instanceof BigDecimal bd ? bd.toPlainString() : String.valueOf(value));
    }

    /** motorUsage -> MOTOR_USAGE, vehicleBodyType -> VEHICLE_BODY_TYPE */
    private static String camelToUpperSnake(String s) {
        return s.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
    }

    /** Login once; never throws (returns null and logs the reason). */
    private String fetchEwayTokenSafe() {
        try {
            String token = fetchEwayToken();
            if (isBlank(token)) {
                log.warn("EwayCommonApi login at {} returned no token - check eway.common.api.login-id / password",
                        ewayBaseUrl);
            }
            return token;
        } catch (Exception ex) {
            log.warn("EwayCommonApi login at {} failed: {}", ewayBaseUrl, ex.getMessage());
            return null;
        }
    }

    /** POST {base}/authentication/login -> Result.Token */
    @SuppressWarnings("unchecked")
    private String fetchEwayToken() {
        Map<String, Object> login = new LinkedHashMap<>();
        login.put("LoginId", ewayLoginId);
        login.put("Password", ewayPassword);
        login.put("ReLoginKey", ewayReLoginKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> res = ewayRestTemplate.postForObject(
                ewayBaseUrl + "/authentication/login", new HttpEntity<>(login, headers), Map.class);
        if (res == null || !(res.get("Result") instanceof Map<?, ?> result)) return null;
        Object t = result.get("Token");
        return t == null ? null : t.toString();
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.toPlainString();
    }

    private static BigDecimal num(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    // -----------------------------------------------------------------------
    // Query - login_user_info (agent name)
    // -----------------------------------------------------------------------

    /**
     * USER_NAME from login_user_info for the policy's LOGIN_ID -> policy_master.agent_name.
     * A native query is used because login_user_info is not mapped as an entity.
     */
    @SuppressWarnings("unchecked")
    private void enrichWithAgentName(HomePositionMasterDTO row) {
        if (isBlank(row.getLoginId())) return;
        try {
            List<Object> found = em.createNativeQuery(
                    "SELECT USER_NAME FROM login_user_info WHERE LOGIN_ID = :loginId")
                    .setParameter("loginId", row.getLoginId())
                    .setMaxResults(1)
                    .getResultList();
            if (!found.isEmpty() && found.get(0) != null) {
                row.setAgentName(String.valueOf(found.get(0)));                            // USER_NAME
            }
        } catch (Exception ex) {
            log.warn("Could not load USER_NAME from login_user_info for LOGIN_ID={}: {}", row.getLoginId(), ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Criteria query 2 - personal_info
    // -----------------------------------------------------------------------

    /**
     * personal_info by CUSTOMER_ID; row of the same COMPANY_ID first, then the newest ENTRY_DATE.
     * Contact columns and ID / holder-type columns are read in separate queries so a problem with
     * one group can never lose the other.
     */
    private void enrichWithPersonalInfo(HomePositionMasterDTO row) {
        if (row.getCustomerId() == null) return;
        String companyId = row.getCompanyId() == null ? "" : String.valueOf(row.getCompanyId());

        try {
            Tuple t = findPersonalInfo(row.getCustomerId(), companyId, "mobileNo1", "email1", "firstName");
            if (t == null) {
                log.warn("No personal_info row found for CUSTOMER_ID={}", row.getCustomerId());
            } else {
                row.setMobileNo(t.get("mobileNo1", String.class));      // MOBILE_NO_1
                row.setEmail(t.get("email1", String.class));            // EMAIL_1
                row.setFirstName(t.get("firstName", String.class));     // FIRST_NAME
            }
        } catch (Exception ex) {
            log.error("Could not load MOBILE_NO_1 / EMAIL_1 / FIRST_NAME for CUSTOMER_ID={}", row.getCustomerId(), ex);
        }

        try {
            Tuple t = findPersonalInfo(row.getCustomerId(), companyId, "idType", "idNumber", "policyHolderType");
            if (t != null) {
                row.setIdProofType(t.get("idType", String.class));              // ID_TYPE
                row.setIdProofNumber(t.get("idNumber", String.class));          // ID_NUMBER
                row.setCustomerType(t.get("policyHolderType", String.class));   // POLICY_HOLDER_TYPE
            }
        } catch (Exception ex) {
            log.error("Could not load ID_TYPE / ID_NUMBER / POLICY_HOLDER_TYPE for CUSTOMER_ID={}", row.getCustomerId(), ex);
        }
    }

    private Tuple findPersonalInfo(String customerId, String companyId, String... attributes) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<PersonalInfo> p = cq.from(PersonalInfo.class);

        List<jakarta.persistence.criteria.Selection<?>> selections = new ArrayList<>();
        for (String a : attributes) selections.add(p.get(a).alias(a));

        cq.multiselect(selections)
          .where(cb.equal(p.<String>get("customerId"), customerId))
          .orderBy(personalInfoOrder(cb, p, companyId));

        List<Tuple> found = em.createQuery(cq).setMaxResults(1).getResultList();
        return found.isEmpty() ? null : found.get(0);
    }

    /** ORDER BY (COMPANY_ID = :companyId) DESC, ENTRY_DATE DESC */
    private List<Order> personalInfoOrder(CriteriaBuilder cb, Root<PersonalInfo> p, String companyId) {
        return List.of(
                cb.desc(cb.<Integer>selectCase()
                        .when(cb.equal(p.<String>get("companyId"), companyId), 1)
                        .otherwise(0)),
                cb.desc(p.get("entryDate")));
    }

    // -----------------------------------------------------------------------
    // Criteria query 3 - motor_data_details
    // -----------------------------------------------------------------------

    /**
     * First vehicle (lowest VEHICLE_ID) of the quote; falls back to POLICY_NO when nothing is found
     * for the QUOTE_NO.
     *
     * @return make/model/company keys for the make-model lookup, or null when nothing was found
     */
    private VehicleKey enrichWithMotorData(HomePositionMasterDTO row) {
        try {
            Tuple t = null;
            if (!isBlank(row.getQuoteNo())) {
                t = findMotorRow("quoteNo", row.getQuoteNo());
            }
            if (t == null && !isBlank(row.getPolicyNo())) {
                t = findMotorRow("policyNo", row.getPolicyNo());
            }
            if (t == null) return null;

            Double sumInsured = t.get("sumInsured", Double.class);
            row.setSumInsured(toBigDecimal(sumInsured));                              // SUM_INSURED
            row.setVehicleRegNo(t.get("registrationNumber", String.class));           // REGISTRATION_NUMBER

            Integer year = t.get("manufactureYear", Integer.class);                   // MANUFACTURE_YEAR
            if (year != null && year >= 1900 && year <= 2100) {
                row.setVehicleMfgDate(LocalDate.of(year, 1, 1));
            }

            row.setMakeId(t.get("vehicleMakeId", String.class));                      // VEHICLE_MAKE_ID
            row.setMakeDescription(t.get("vehicleMakeDesc", String.class));           // VEHICLE_MAKE_DESC
            row.setModelId(t.get("vehicleModelId", String.class));                    // VEHICLE_MODEL_ID
            row.setModelDescription(t.get("vehicleModelDesc", String.class));         // VEHCILE_MODEL_DESC
            row.setBodytypeId(t.get("vehicleType", String.class));                    // VEHICLE_TYPE
            row.setBodytypeDesc(t.get("vehicleTypeDesc", String.class));              // VEHICLE_TYPE_DESC
            row.setVehicleusageId(t.get("motorUsage", String.class));                 // MOTOR_USAGE
            row.setVehicleusageDesc(t.get("motorUsageDesc", String.class));           // MOTOR_USAGE_DESC
            row.setPolicyType(t.get("policyType", String.class));                     // POLICY_TYPE
            row.setRequestReferenceNo(t.get("requestReferenceNo", String.class));   // REQUEST_REFERENCE_NO
            row.setLocationId(t.get("locationId", Integer.class));   // LOCATION_ID
            row.setVehicleId(t.get("vehicleId", String.class));   // VEHICLE_ID
            row.setWindScreenCoverRequeired(t.get("windScreenCoverRequeired", String.class));   // WIND_SCREEN_COVER_REQUEIRED
            row.setInsuranceType(t.get("insuranceType", String.class));   // INSURANCE_TYPE
            row.setChassisNumber(t.get("chassisNumber", String.class));   // CHASSIS_NUMBER
            row.setFuelType(t.get("fuelType", String.class));   // FUEL_TYPE
            row.setSeatingCapacity(t.get("seatingCapacity", Integer.class));   // SEATING_CAPACITY
            row.setPeriodOfInsurance(t.get("periodOfInsurance", String.class));   // PERIOD_OF_INSURANCE
            row.setWindScreenSumInsured(toBigDecimal(t.get("windScreenSumInsured", Double.class)));   // WIND_SCREEN_SUM_INSURED
            row.setAccessoriesSumInsured(toBigDecimal(t.get("accessoriesSumInsured", Double.class)));   // ACCCESSORIES_SUM_INSURED
            row.setNcdYears(t.get("ncdYears", Integer.class));   // NCDYEARS
            row.setNcdYn(t.get("ncdYn", String.class));   // NCD_YN
            row.setVdRefNo(t.get("vdRefNo", Long.class));   // VD_REFNO
            row.setEntryDate(t.get("entryDate", LocalDateTime.class));   // ENTRY_DATE
            row.setExchangeRate(toBigDecimal(t.get("exchangeRate", Double.class)));   // EXCHANGE_RATE
            row.setFleetOwnerYn(t.get("fleetOwnerYn", String.class));   // FLEET_OWNER_YN
            row.setNoOfVehicles(t.get("noOfVehicles", Integer.class));   // NO_OF_VEHICLES
            row.setHavePromoCode(t.get("havePromoCode", String.class));   // HAVEPROMOCODE
            row.setWindScreenSumInsuredLc(toBigDecimal(t.get("windScreenSumInsuredLc", Double.class)));   // WIND_SCREEN_SUM_INSURED_LC
            row.setAccessoriesSumInsuredLc(toBigDecimal(t.get("accessoriesSumInsuredLc", Double.class)));   // ACCCESSORIES_SUM_INSURED_LC
            row.setSumInsuredLc(toBigDecimal(t.get("sumInsuredLc", Double.class)));   // SUM_INSURED_LC
            row.setCubicCapacity(toBigDecimal(t.get("cubicCapacity", Double.class)));   // CUBIC_CAPACITY
            row.setDrivenBy(t.get("drivenBy", String.class));   // DRIVEN_BY
            row.setCarAlarmYn(t.get("carAlarmYn", String.class));   // CAR_ALARM_YN
            row.setPaymentFrequency(t.get("paymentFrequency", Integer.class));   // PAYMENT_FREQUENCY
            row.setPreviousInsuranceYn(t.get("previousInsuranceYn", String.class));   // PREVIOUS_INSURANCE_YN
            row.setZone(t.get("zone", Integer.class));   // ZONE

            return new VehicleKey(
                    t.get("vehicleMakeId", String.class),
                    t.get("vehicleModelId", String.class),
                    t.get("companyId", String.class));
        } catch (Exception ex) {
            log.warn("Could not load motor_data_details for POLICY_NO={}: {}", row.getPolicyNo(), ex.getMessage());
            return null;
        }
    }

    /** @param attribute "quoteNo" or "policyNo" */
    private Tuple findMotorRow(String attribute, String value) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<MotorDataDetails> m = cq.from(MotorDataDetails.class);

        cq.multiselect(
                m.<Double>get("sumInsured").alias("sumInsured"),
                m.<String>get("registrationNumber").alias("registrationNumber"),
                m.<Integer>get("manufactureYear").alias("manufactureYear"),
                m.<String>get("vehicleMakeId").alias("vehicleMakeId"),
                m.<String>get("vehicleMakeDesc").alias("vehicleMakeDesc"),
                m.<String>get("vehicleModelId").alias("vehicleModelId"),
                m.<String>get("vehicleModelDesc").alias("vehicleModelDesc"),
                m.<String>get("vehicleType").alias("vehicleType"),
                m.<String>get("vehicleTypeDesc").alias("vehicleTypeDesc"),
                m.<String>get("motorUsage").alias("motorUsage"),
                m.<String>get("motorUsageDesc").alias("motorUsageDesc"),
                m.<String>get("policyType").alias("policyType"),
                m.<String>get("requestReferenceNo").alias("requestReferenceNo"),
                m.<Integer>get("locationId").alias("locationId"),
                m.<String>get("vehicleId").alias("vehicleId"),
                m.<String>get("windScreenCoverRequeired").alias("windScreenCoverRequeired"),
                m.<String>get("insuranceType").alias("insuranceType"),
                m.<String>get("chassisNumber").alias("chassisNumber"),
                m.<String>get("fuelType").alias("fuelType"),
                m.<Integer>get("seatingCapacity").alias("seatingCapacity"),
                m.<String>get("periodOfInsurance").alias("periodOfInsurance"),
                m.<Double>get("windScreenSumInsured").alias("windScreenSumInsured"),
                m.<Double>get("accessoriesSumInsured").alias("accessoriesSumInsured"),
                m.<Integer>get("ncdYears").alias("ncdYears"),
                m.<String>get("ncdYn").alias("ncdYn"),
                m.<Long>get("vdRefNo").alias("vdRefNo"),
                m.<LocalDateTime>get("entryDate").alias("entryDate"),
                m.<Double>get("exchangeRate").alias("exchangeRate"),
                m.<String>get("fleetOwnerYn").alias("fleetOwnerYn"),
                m.<Integer>get("noOfVehicles").alias("noOfVehicles"),
                m.<String>get("havePromoCode").alias("havePromoCode"),
                m.<Double>get("windScreenSumInsuredLc").alias("windScreenSumInsuredLc"),
                m.<Double>get("accessoriesSumInsuredLc").alias("accessoriesSumInsuredLc"),
                m.<Double>get("sumInsuredLc").alias("sumInsuredLc"),
                m.<Double>get("cubicCapacity").alias("cubicCapacity"),
                m.<String>get("drivenBy").alias("drivenBy"),
                m.<String>get("carAlarmYn").alias("carAlarmYn"),
                m.<Integer>get("paymentFrequency").alias("paymentFrequency"),
                m.<String>get("previousInsuranceYn").alias("previousInsuranceYn"),
                m.<Integer>get("zone").alias("zone"),
                m.<String>get("companyId").alias("companyId"))
          .where(cb.equal(m.<String>get(attribute), value))
          .orderBy(cb.asc(m.get("vehicleId")));

        List<Tuple> found = em.createQuery(cq).setMaxResults(1).getResultList();
        return found.isEmpty() ? null : found.get(0);
    }

    // -----------------------------------------------------------------------
    // Criteria query 4 - motor_driver_details
    // -----------------------------------------------------------------------

    /** DRIVER_DOB for the quote. */
    private void enrichWithDriverDob(HomePositionMasterDTO row) {
        if (isBlank(row.getQuoteNo())) return;
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<LocalDate> cq = cb.createQuery(LocalDate.class);
            Root<MotorDriverDetails> d = cq.from(MotorDriverDetails.class);
            cq.select(d.<LocalDate>get("driverDob"))
              .where(cb.equal(d.<String>get("quoteNo"), row.getQuoteNo()),
                     cb.isNotNull(d.<LocalDate>get("driverDob")));

            List<LocalDate> found = em.createQuery(cq).setMaxResults(1).getResultList();
            if (!found.isEmpty()) {
                row.setDriverDob(found.get(0));                                       // DRIVER_DOB
            }
        } catch (Exception ex) {
            log.warn("Could not load DRIVER_DOB for POLICY_NO={}: {}", row.getPolicyNo(), ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Query 5 - eway_motor_makemodel_master (all columns)
    // -----------------------------------------------------------------------

    /**
     * Latest row (AMEND_ID / EFFECTIVE_DATE_END desc) for MAKE_ID + MODEL_ID + COMPANY_ID.
     * VEH_MANF_COUNTRY goes to vehicleMfgCountry; every OTHER column is put in extraAttributes (JSON).
     * A native SELECT * is used because the full column list of the table is not mapped in the entity.
     */
    @SuppressWarnings("unchecked")
    private void enrichWithMakeModel(HomePositionMasterDTO row, VehicleKey key) {
        if (key == null) return;
        Integer makeId = parseIntSafe(key.makeId());
        Integer modelId = parseIntSafe(key.modelId());
        if (makeId == null || modelId == null) return;
        try {
            List<Tuple> found = em.createNativeQuery(
                    "SELECT * FROM eway_motor_makemodel_master "
                  + "WHERE MAKE_ID = :makeId AND MODEL_ID = :modelId AND COMPANY_ID = :companyId "
                  + "ORDER BY AMEND_ID DESC, EFFECTIVE_DATE_END DESC", Tuple.class)
                    .setParameter("makeId", makeId)
                    .setParameter("modelId", modelId)
                    .setParameter("companyId", key.companyId())
                    .setMaxResults(1)
                    .getResultList();
            if (found.isEmpty()) return;

            Map<String, Object> extra = new LinkedHashMap<>();
            for (TupleElement<?> el : found.get(0).getElements()) {
                String column = el.getAlias();
                Object value = found.get(0).get(column);
                if ("VEH_MANF_COUNTRY".equalsIgnoreCase(column)) {
                    row.setVehicleMfgCountry(value == null ? null : String.valueOf(value));   // VEH_MANF_COUNTRY
                } else {
                    extra.put(column, jsonSafe(value));
                }
            }
            row.setExtraAttributes(extra);
        } catch (Exception ex) {
            log.warn("Could not load eway_motor_makemodel_master for POLICY_NO={}: {}", row.getPolicyNo(), ex.getMessage());
        }
    }

    /** Timestamps / dates -> ISO text; numbers, booleans, strings unchanged; anything else -> toString. */
    private Object jsonSafe(Object v) {
        if (v == null || v instanceof Number || v instanceof Boolean || v instanceof String) return v;
        if (v instanceof Timestamp ts) return ts.toLocalDateTime().toString();
        if (v instanceof java.sql.Date d) return d.toLocalDate().toString();
        return String.valueOf(v);
    }

    // -----------------------------------------------------------------------
    // policy_master mapping (policy_master ONLY - no mst_* inserts)
    // -----------------------------------------------------------------------

    /**
     * Value stored in policy_master.policy_no: ORIGINAL_POLICY_NO when present, otherwise POLICY_NO
     * (a policy that was never endorsed has no ORIGINAL_POLICY_NO).
     */
    private String resolvePolicyNoForInsert(HomePositionMasterDTO r) {
        return isBlank(r.getOriginalPolicyNo()) ? r.getPolicyNo() : r.getOriginalPolicyNo().trim();
    }

    private PolicyMasterRecord toPolicyMasterRecord(HomePositionMasterDTO r, String policyNoToInsert) {
        return PolicyMasterRecord.builder()
                .policyNo(policyNoToInsert)
                .productId(r.getProductId())
                .customerId(r.getCustomerId())
                .branchId(parseIntSafe(r.getBranchId()))          // int column
                .agentId(parseIntSafe(r.getAgentId()))            // int column
                .agentName(r.getAgentName())                      // login_user_info.USER_NAME
                .sumInsured(r.getSumInsured() != null ? r.getSumInsured() : BigDecimal.ZERO)   // NOT NULL column
                .policyStartDate(r.getPolicyStartDate())
                .policyExpiryDate(r.getPolicyExpiryDate())
                .vehicleRegNo(r.getVehicleRegNo())
                .vehicleMfgDate(r.getVehicleMfgDate())
                .vehicleMfgCountry(r.getVehicleMfgCountry())
                .driverDob(r.getDriverDob())
                .policyStatus(toPolicyStatusEnum(r.getPolicyStatus()))
                .extraAttributes(toJson(r.getExtraAttributes()))
                .companyId(r.getCompanyId())
                .productName(r.getProductName())
                .customerName(r.getCustomerName())
                .branchName(r.getBranchName())
                .sectionId(r.getSectionId())
                .currency(r.getCurrency())
                .premiumLc(r.getPremiumLc())
                .premiumFc(r.getPremiumFc())
                .overallPremiumFc(r.getPremiumIncludedTax() != null ? r.getPremiumIncludedTax() : r.getOverallPremiumFc())
                .overallPremiumLc(r.getPremiumIncludedTaxLC() != null ? r.getPremiumIncludedTaxLC() : r.getOverallPremiumLc())
                // final premium = overall premium at insert time (no loading/discount yet); the renewal batch
                // / recalculate later stores overall x (1 + net loading% / discount%) here
                .finalPremiumFc(r.getPremiumIncludedTax() != null ? r.getPremiumIncludedTax() : r.getOverallPremiumFc())
                .finalPremiumLc(r.getPremiumIncludedTaxLC() != null ? r.getPremiumIncludedTaxLC() : r.getOverallPremiumLc())
                .sourceType(r.getSourceType())
                .quoteNo(r.getQuoteNo())
                .endtCount(r.getEndtCount())
                .mobileNo(r.getMobileNo())
                .email(r.getEmail())
                .firstName(r.getFirstName())
                .idProofType(r.getIdProofType())
                .idProofNumber(r.getIdProofNumber())
                .applicationId(r.getApplicationId())
                .loginId(r.getLoginId())
                .brokerCode(r.getBrokerCode())
                .accountExecutiveId(r.getAccountExecutiveId())
                .makeId(parseIntSafe(r.getMakeId()))              // int column ("" -> null)
                .makeDescription(r.getMakeDescription())
                .modelId(parseIntSafe(r.getModelId()))            // int column
                .modelDescription(r.getModelDescription())
                .bodytypeId(parseIntSafe(r.getBodytypeId()))      // int column
                .bodytypeDesc(r.getBodytypeDesc())
                .vehicleusageId(parseIntSafe(r.getVehicleusageId()))   // int column
                .vehicleusageDesc(r.getVehicleusageDesc())
                .customerType(r.getCustomerType())
                .policyTerm(r.getPolicyTerm())
                .noClaimDiscount(r.getNoClaimDiscount())
                .policyType(r.getPolicyType())
                .requestReferenceNo(r.getRequestReferenceNo())
                .locationId(r.getLocationId())
                .vehicleId(r.getVehicleId())
                .windScreenCoverRequeired(r.getWindScreenCoverRequeired())
                .insuranceType(r.getInsuranceType())
                .chassisNumber(r.getChassisNumber())
                .fuelType(r.getFuelType())
                .seatingCapacity(r.getSeatingCapacity())
                .periodOfInsurance(r.getPeriodOfInsurance())
                .windScreenSumInsured(r.getWindScreenSumInsured())
                .accessoriesSumInsured(r.getAccessoriesSumInsured())
                .ncdYears(r.getNcdYears())
                .ncdYn(r.getNcdYn())
                .vdRefNo(r.getVdRefNo())
                .entryDate(r.getEntryDate())
                .exchangeRate(r.getExchangeRate())
                .fleetOwnerYn(r.getFleetOwnerYn())
                .noOfVehicles(r.getNoOfVehicles())
                .havePromoCode(r.getHavePromoCode())
                .windScreenSumInsuredLc(r.getWindScreenSumInsuredLc())
                .accessoriesSumInsuredLc(r.getAccessoriesSumInsuredLc())
                .sumInsuredLc(r.getSumInsuredLc())
                .cubicCapacity(r.getCubicCapacity())
                .drivenBy(r.getDrivenBy())
                .carAlarmYn(r.getCarAlarmYn())
                .paymentFrequency(r.getPaymentFrequency())
                .previousInsuranceYn(r.getPreviousInsuranceYn())
                .zone(r.getZone())
                .build();
    }

    /**
     * policy_master.policy_status is enum('ACTIVE','EXPIRED','LAPSED'). home_position_master.STATUS = 'P'
     * (policy issued) is therefore stored as ACTIVE. Change here if the column is later widened to varchar.
     */
    private String toPolicyStatusEnum(String sourceStatus) {
        if (sourceStatus == null) return "ACTIVE";
        return switch (sourceStatus.trim().toUpperCase()) {
            case "EXPIRED" -> "EXPIRED";
            case "LAPSED"  -> "LAPSED";
            default        -> "ACTIVE";      // 'P', 'ACTIVE', ...
        };
    }

    private String toJson(Map<String, Object> attrs) {
        if (attrs == null || attrs.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(attrs);
        } catch (Exception ex) {
            log.warn("Could not serialize extraattributes: {}", ex.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static BigDecimal toBigDecimal(Double d) {
        return d == null ? null : BigDecimal.valueOf(d);
    }

    private Integer parseIntSafe(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("Expected a numeric value but got '{}' - leaving field null", value);
            return null;
        }
    }
}
