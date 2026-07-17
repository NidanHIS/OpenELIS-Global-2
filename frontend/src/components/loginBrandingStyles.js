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
  labContactNumberStyle: {
    fontSize: branding.loginLabContactNumberFontSize || "0.875rem",
    color: branding.loginLabContactNumberColor || "inherit",
    marginBottom: "0.25rem",
  },
  labEmailStyle: {
    fontSize: branding.loginLabEmailFontSize || "0.875rem",
    color: branding.loginLabEmailColor || "inherit",
  },
});
