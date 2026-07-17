package org.openelisglobal.sitebranding.form;

import jakarta.validation.constraints.Size;

/**
 * Form object for SiteBranding entity - used for REST API input/output
 * 
 * Task Reference: T016
 */
public class SiteBrandingForm {

    private Integer id;

    private String headerLogoUrl;

    private String loginLogoUrl;

    private Boolean useHeaderLogoForLogin = false;

    private String faviconUrl;

    @Size(max = 50, message = "Header color must not exceed 50 characters")
    private String headerColor;

    @Size(max = 50, message = "Primary color must not exceed 50 characters")
    private String primaryColor;

    @Size(max = 50, message = "Secondary color must not exceed 50 characters")
    private String secondaryColor;

    @Size(max = 10, message = "Color mode must not exceed 10 characters")
    private String colorMode;

    @Size(max = 20, message = "Login site name font size must not exceed 20 characters")
    private String loginSiteNameFontSize;

    @Size(max = 50, message = "Login site name color must not exceed 50 characters")
    private String loginSiteNameColor;

    @Size(max = 20, message = "Login additional site info font size must not exceed 20 characters")
    private String loginAdditionalSiteInfoFontSize;

    @Size(max = 50, message = "Login additional site info color must not exceed 50 characters")
    private String loginAdditionalSiteInfoColor;

    @Size(max = 20, message = "Login lab contact number font size must not exceed 20 characters")
    private String loginLabContactNumberFontSize;

    @Size(max = 50, message = "Login lab contact number color must not exceed 50 characters")
    private String loginLabContactNumberColor;

    @Size(max = 20, message = "Login lab email font size must not exceed 20 characters")
    private String loginLabEmailFontSize;

    @Size(max = 50, message = "Login lab email color must not exceed 50 characters")
    private String loginLabEmailColor;

    private String lastModified;

    private String lastModifiedBy;

    // Getters and Setters

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getHeaderLogoUrl() {
        return headerLogoUrl;
    }

    public void setHeaderLogoUrl(String headerLogoUrl) {
        this.headerLogoUrl = headerLogoUrl;
    }

    public String getLoginLogoUrl() {
        return loginLogoUrl;
    }

    public void setLoginLogoUrl(String loginLogoUrl) {
        this.loginLogoUrl = loginLogoUrl;
    }

    public Boolean getUseHeaderLogoForLogin() {
        return useHeaderLogoForLogin;
    }

    public void setUseHeaderLogoForLogin(Boolean useHeaderLogoForLogin) {
        this.useHeaderLogoForLogin = useHeaderLogoForLogin;
    }

    public String getFaviconUrl() {
        return faviconUrl;
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public String getHeaderColor() {
        return headerColor;
    }

    public void setHeaderColor(String headerColor) {
        this.headerColor = headerColor;
    }

    public String getColorMode() {
        return colorMode;
    }

    public void setColorMode(String colorMode) {
        this.colorMode = colorMode;
    }

    public String getLoginSiteNameFontSize() {
        return loginSiteNameFontSize;
    }

    public void setLoginSiteNameFontSize(String loginSiteNameFontSize) {
        this.loginSiteNameFontSize = loginSiteNameFontSize;
    }

    public String getLoginSiteNameColor() {
        return loginSiteNameColor;
    }

    public void setLoginSiteNameColor(String loginSiteNameColor) {
        this.loginSiteNameColor = loginSiteNameColor;
    }

    public String getLoginAdditionalSiteInfoFontSize() {
        return loginAdditionalSiteInfoFontSize;
    }

    public void setLoginAdditionalSiteInfoFontSize(String loginAdditionalSiteInfoFontSize) {
        this.loginAdditionalSiteInfoFontSize = loginAdditionalSiteInfoFontSize;
    }

    public String getLoginAdditionalSiteInfoColor() {
        return loginAdditionalSiteInfoColor;
    }

    public void setLoginAdditionalSiteInfoColor(String loginAdditionalSiteInfoColor) {
        this.loginAdditionalSiteInfoColor = loginAdditionalSiteInfoColor;
    }

    public String getLoginLabContactNumberFontSize() {
        return loginLabContactNumberFontSize;
    }

    public void setLoginLabContactNumberFontSize(String loginLabContactNumberFontSize) {
        this.loginLabContactNumberFontSize = loginLabContactNumberFontSize;
    }

    public String getLoginLabContactNumberColor() {
        return loginLabContactNumberColor;
    }

    public void setLoginLabContactNumberColor(String loginLabContactNumberColor) {
        this.loginLabContactNumberColor = loginLabContactNumberColor;
    }

    public String getLoginLabEmailFontSize() {
        return loginLabEmailFontSize;
    }

    public void setLoginLabEmailFontSize(String loginLabEmailFontSize) {
        this.loginLabEmailFontSize = loginLabEmailFontSize;
    }

    public String getLoginLabEmailColor() {
        return loginLabEmailColor;
    }

    public void setLoginLabEmailColor(String loginLabEmailColor) {
        this.loginLabEmailColor = loginLabEmailColor;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }
}
