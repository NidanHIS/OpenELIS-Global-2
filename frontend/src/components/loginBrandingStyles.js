export const getLoginSiteInfoStyles = (branding = {}) => ({
  siteNameStyle: {
    fontSize: branding.loginSiteNameFontSize || "1.25rem",
    fontWeight: "700",
    color: branding.loginSiteNameColor || "inherit",
    marginBottom: "0.5rem",
  },
  additionalSiteInfoStyle: {
    fontSize: branding.loginAdditionalSiteInfoFontSize || "0.875rem",
    color: branding.loginAdditionalSiteInfoColor || "inherit",
    marginBottom: "0.5rem",
  },
  hardcodedLabelStyle: {
    fontSize: "1.5rem",
    fontWeight: "700",
    color: branding.headerColor || "#295785",
    marginTop: "0.25rem",
    lineHeight: 1.2,
    display: "block",
    textAlign: "center",
    width: "100%",
  },
});
