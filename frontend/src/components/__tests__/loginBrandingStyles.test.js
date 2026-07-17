import { getLoginSiteInfoStyles } from "../loginBrandingStyles";

describe("getLoginSiteInfoStyles", () => {
  it("uses the branding payload values for login-page site info styling", () => {
    const styles = getLoginSiteInfoStyles({
      loginSiteNameFontSize: "2rem",
      loginSiteNameColor: "#ff0000",
      loginAdditionalSiteInfoFontSize: "1rem",
      loginAdditionalSiteInfoColor: "#00ff00",
      loginLabContactNumberFontSize: "0.95rem",
      loginLabContactNumberColor: "#0000ff",
      loginLabEmailFontSize: "0.9rem",
      loginLabEmailColor: "#123456",
    });

    expect(styles.siteNameStyle.fontSize).toBe("2rem");
    expect(styles.siteNameStyle.color).toBe("#ff0000");
    expect(styles.additionalSiteInfoStyle.fontSize).toBe("1rem");
    expect(styles.additionalSiteInfoStyle.color).toBe("#00ff00");
    expect(styles.hardcodedLabelStyle.fontSize).toBe("1.5rem");
    expect(styles.hardcodedLabelStyle.color).toBe("#295785");
  });
});
