package bo.edu.ficct.sw2.vm3gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integrations")
public class IntegrationProperties {

    private String vm1BaseUrl;
    private String vm2BaseUrl;
    private String vm2JwtToken;

    public String getVm1BaseUrl() {
        return normalize(vm1BaseUrl);
    }

    public void setVm1BaseUrl(String vm1BaseUrl) {
        this.vm1BaseUrl = vm1BaseUrl;
    }

    public String getVm2BaseUrl() {
        return normalize(vm2BaseUrl);
    }

    public void setVm2BaseUrl(String vm2BaseUrl) {
        this.vm2BaseUrl = vm2BaseUrl;
    }

    public String getVm2JwtToken() {
        return vm2JwtToken == null ? "" : vm2JwtToken.trim();
    }

    public void setVm2JwtToken(String vm2JwtToken) {
        this.vm2JwtToken = vm2JwtToken;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
