package com.wso2.custom.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import org.apache.axiom.util.base64.Base64Utils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.core.util.KeyStoreManager;
import org.wso2.carbon.registry.core.exceptions.RegistryException;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/*
 * Purpose of this extension is to read a given set of HTTP headers from incoming request and create a JWT based on
 * those values. HTTP header:value will be part of claims in the JWT.
 *
 * Requirement is to forward a set of HTTP headers to backend services and verify these headers are not tampered with
 *
 * Tested with API Manager 2.1.0
 *
 * Usage:
 *     <class name="JWTFromRequestHeaders">
 *         <property name="outgoingHeaderName" value="CustomJWT" />
 *         <property name="includeTheseHeaders" value="Comma,Separated,List,Of,Request,Header,Names" />
 *         <!-- optional, defaults to 3600 (1 hour) -->
 *         <property name="expirationTimeInSeconds" value="3600" />
 *     </class>
 */
public class JWTFromRequestHeaders extends AbstractMediator {

    public JWTFromRequestHeaders() {}

    public String getOutgoingHeaderName() {
        return outgoingHeaderName;
    }

    public void setOutgoingHeaderName(String outgoingHeaderName) {
        this.outgoingHeaderName = outgoingHeaderName;
    }

    public String getIncludeTheseHeaders() {
        return includeTheseHeaders;
    }

    public void setIncludeTheseHeaders(String includeTheseHeaders) {
        this.includeTheseHeaders = includeTheseHeaders;
    }

    public int getExpirationTimeInSeconds() { return expirationTimeInSeconds; }

    public void setExpirationTimeInSeconds(int expirationTimeInSeconds) { this.expirationTimeInSeconds = expirationTimeInSeconds; }

    // Custom JWT created by this class will be attached to this outgoing transport header
    public String outgoingHeaderName;

    // Comma separated list of custom HTTP headers to include in JWT
    public String includeTheseHeaders;

    public int expirationTimeInSeconds;

    public boolean mediate(MessageContext messageContext) {
        SynapseLog log = getLog(messageContext);

        HashMap<String, String> requestHeaders = new HashMap<String, String>();

        if (includeTheseHeaders.equals("")) {
            log.error("No headers specified for building custom JWT. Header will not be included in outgoing response");

            // Continue mediation flow
            return true;
        }

        Map<String, Object> trpHeaders = (Map<String, Object>)
                ((Axis2MessageContext) messageContext).getAxis2MessageContext().getProperty("TRANSPORT_HEADERS");
        for (String header : includeTheseHeaders.split(",")) {
            if (trpHeaders.get(header) != null) {
                requestHeaders.put(header, (String) trpHeaders.get(header));
            }
        }

        String jwtHeader = null;
        StringBuilder jwtHeaderBuilder = new StringBuilder();
        jwtHeaderBuilder.append("{\"typ\":\"JWT\",");
        jwtHeaderBuilder.append("\"alg\":\"");
        jwtHeaderBuilder.append("RS256");
        jwtHeaderBuilder.append('\"');
        jwtHeaderBuilder.append('}');

        jwtHeader = jwtHeaderBuilder.toString();
        String base64UrlEncodedHeader = null;
        try {
            base64UrlEncodedHeader = Base64Utils.encode(jwtHeader.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }

        JWTClaimsSet claimsSet = new JWTClaimsSet();
        for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
            claimsSet.setClaim(header.getKey(), header.getValue());
        }

        // JWT expiration, defaults to one hour
        int exp = (expirationTimeInSeconds != 0) ? expirationTimeInSeconds : 3600;
        claimsSet.setClaim("exp", new Date(System.currentTimeMillis()+ (exp*1000)));

        String jwtBody = claimsSet.toJSONObject().toJSONString();
        String base64UrlEncodedBody = null;
        try {
            base64UrlEncodedBody = Base64Utils.encode(jwtBody.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }

        String assertion = base64UrlEncodedHeader + '.' + base64UrlEncodedBody;

        PrivilegedCarbonContext cc = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = cc.getTenantId();
        String tenantDomain = cc.getTenantDomain();

        Key privateKey = null;

        try {
            APIUtil.loadTenantRegistry(tenantId);
        } catch (RegistryException e) {
            log.error(ExceptionUtils.getStackTrace(e));

            // Cannot continue without being able to load registry
            return false;
        }

        KeyStoreManager ksm = KeyStoreManager.getInstance(tenantId);

        if (!org.wso2.carbon.base.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
            String ksName = tenantDomain.trim().replace('.', '-');
            String jksName = ksName + ".jks";
            privateKey = ksm.getPrivateKey(jksName, tenantDomain);
        } else {
            try {
                privateKey = ksm.getDefaultPrivateKey();
            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
            }
        }

        Signature signature = null;
        try {
            signature = Signature.getInstance("SHA256withRSA");
            signature.initSign((PrivateKey) privateKey);

            byte[] dataInBytes;
            dataInBytes = assertion.getBytes(Charset.defaultCharset());
            signature.update(dataInBytes);

            byte[] signedAssertion = signature.sign();

            String base64UrlEncodedAssertion = Base64Utils.encode(signedAssertion);

            String customJWT =  base64UrlEncodedHeader + '.' + base64UrlEncodedBody + '.' + base64UrlEncodedAssertion;

            ((Map<String, Object>) ((Axis2MessageContext) messageContext).getAxis2MessageContext()
                    .getProperty("TRANSPORT_HEADERS")).put(outgoingHeaderName, customJWT);

        } catch (NoSuchAlgorithmException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        } catch (InvalidKeyException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        } catch (SignatureException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }

        return true;
    }
}
