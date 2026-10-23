package com.insurance.renewal.service;

import com.insurance.renewal.entity.PolicyMasterRecord;
import com.insurance.renewal.entity.RenewalProcessing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends the renewal notification mail by calling EwayCommonApi
 * {@code POST /post/notification/pushnotification} (no login token needed - the endpoint is in eway's NOAUTH list).
 * Used by the renewal batch after the policy was pulled, the premium calculated and the processing record saved.
 * Never throws: a failed mail must not stop the batch.
 */
@Slf4j
@Service
public class EwayNotificationService {

    private static final DateTimeFormatter EWAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Value("${eway.common.api.base-url}")
    private String ewayBaseUrl;
    @Value("${eway.notification.enabled:true}")
    private boolean enabled;
    /** Name of the mail template in eway's notification template master. */
    @Value("${eway.notification.template-name:RENEWAL-MAIL}")
    private String templateName;
    /** Phone / messenger country code sent with the customer (eway validation needs a value). */
    @Value("${eway.notification.phone-code:264}")
    private Integer phoneCode;

    private final RestTemplate restTemplate = new RestTemplate();

    /** @return true when eway accepted the request (HTTP 2xx) */
    public boolean sendRenewalMail(PolicyMasterRecord policy, RenewalProcessing processing) {
        if (!enabled) return false;
        try {
            Map<String, Object> body = buildRequest(policy, processing);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<?, ?> res = restTemplate.postForObject(ewayBaseUrl + "/post/notification/pushnotification",
                    new HttpEntity<>(body, headers), Map.class);
            log.info("Renewal mail request sent for POLICY_NO={} -> eway response {}", policy.getPolicyNo(), res);
            return true;
        } catch (Exception ex) {
            log.warn("Renewal mail request failed for POLICY_NO={}: {}", policy.getPolicyNo(), ex.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildRequest(PolicyMasterRecord p, RenewalProcessing proc) {
        BigDecimal phone = toDecimal(p.getMobileNo());

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("Customername", p.getCustomerName());
        customer.put("Customermailid", p.getEmail());
        customer.put("Customerphoneno", phone);
        customer.put("Customerphonecode", phoneCode);
        customer.put("Customermessengercode", phoneCode);
        customer.put("Customermessengerphone", phone);
        customer.put("CustomerRefno", p.getCustomerId());

        BigDecimal premium = p.getFinalPremiumLc() != null ? p.getFinalPremiumLc()
                : (proc != null ? proc.getFinalPremiumAmount() : p.getOverallPremiumLc());

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("Companyname", p.getCompanyId() == null ? null : String.valueOf(p.getCompanyId()));
        req.put("Productname", p.getProductName());
        req.put("Sectionname", p.getSectionId() == null ? null : String.valueOf(p.getSectionId()));
        req.put("Statusmessage", proc != null && proc.getResolutionStatus() != null
                ? proc.getResolutionStatus().name() : "RENEWAL DUE");
        req.put("Policyno", p.getPolicyNo());
        req.put("Quoteno", p.getQuoteNo());
        req.put("Notifdescription", "Policy " + p.getPolicyNo() + " is due for renewal on " + p.getPolicyExpiryDate());
        req.put("Notifcationdate", new Date().getTime());          // epoch millis
        req.put("Notifpriority", 1);
        req.put("Notiftemplatename", templateName);
        req.put("Notifpushedstatus", "PENDING");
        req.put("Customer", customer);
        req.put("CompanyId", p.getCompanyId() == null ? null : String.valueOf(p.getCompanyId()));
        req.put("ProductId", p.getProductId());
        req.put("PushedBy", "RENEWAL-BATCH");
        req.put("BranchCode", p.getBranchId() == null ? null : String.valueOf(p.getBranchId()));
        req.put("RequestReferenceNo", p.getRequestReferenceNo());
        req.put("RegistrationNo", p.getVehicleRegNo());
        req.put("InsuranceClass", p.getInsuranceType());
        req.put("PolicyStartDate", p.getPolicyStartDate() == null ? null : p.getPolicyStartDate().format(EWAY_DATE));
        req.put("PolicyEndDate", p.getPolicyExpiryDate() == null ? null : p.getPolicyExpiryDate().format(EWAY_DATE));
        req.put("PremiumAmount", premium == null ? null : premium.toPlainString());
        return req;
    }

    private static BigDecimal toDecimal(String s) {
        if (s == null) return BigDecimal.ZERO;
        String digits = s.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? BigDecimal.ZERO : new BigDecimal(digits);
    }
}
