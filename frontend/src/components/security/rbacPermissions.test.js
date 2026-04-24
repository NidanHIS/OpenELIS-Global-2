import {
  buildPermissionContext,
  hasAnyPermission,
  hasPermission,
  mapLegacyRoleRequirementsToPermissions,
  PERMISSIONS,
} from "./rbacPermissions";
import { Roles } from "../utils/Utils";

describe("rbacPermissions", () => {
  test("builds global and lab permission contexts from session details", () => {
    const userSessionDetails = {
      roles: [Roles.USER_ACCOUNT_ADMIN],
      userLabRolesMap: {
        AllLabUnits: [Roles.RESULTS],
        Hematology: [Roles.VALIDATION],
      },
    };

    const context = buildPermissionContext(userSessionDetails);

    expect(context.globalPermissions.has(PERMISSIONS.USER_READ)).toBe(true);
    expect(context.globalPermissions.has(PERMISSIONS.USER_WRITE)).toBe(true);
    expect(context.labPermissions.get("*").has(PERMISSIONS.RESULTS_ENTER)).toBe(
      true,
    );
    expect(
      context.labPermissions.get("Hematology").has(PERMISSIONS.RESULTS_VALIDATE),
    ).toBe(true);
  });

  test("checks global, all-labs, and lab-specific permissions", () => {
    const userSessionDetails = {
      roles: [Roles.AUDIT_TRAIL],
      userLabRolesMap: {
        AllLabUnits: [Roles.RESULTS],
        Biochemistry: [Roles.VALIDATION],
      },
    };

    expect(
      hasPermission(userSessionDetails, PERMISSIONS.AUDIT_READ),
    ).toBeTruthy();
    expect(
      hasPermission(
        userSessionDetails,
        PERMISSIONS.RESULTS_ENTER,
        "Immunohistochemistry",
      ),
    ).toBeTruthy();
    expect(
      hasPermission(
        userSessionDetails,
        PERMISSIONS.RESULTS_VALIDATE,
        "Biochemistry",
      ),
    ).toBeTruthy();
    expect(
      hasPermission(userSessionDetails, PERMISSIONS.RESULTS_VALIDATE, "Cytology"),
    ).toBeFalsy();
  });

  test("maps legacy role and labUnitRole requirements into granular permissions", () => {
    const permissions = mapLegacyRoleRequirementsToPermissions({
      role: [Roles.RECEPTION, Roles.REPORTS],
      labUnitRole: { Cytology: [Roles.VALIDATION] },
    });

    expect(permissions).toEqual(
      expect.arrayContaining([
        PERMISSIONS.PATIENT_READ,
        PERMISSIONS.ORDER_CREATE,
        PERMISSIONS.REPORT_READ,
        PERMISSIONS.RESULTS_VALIDATE,
      ]),
    );
  });

  test("hasAnyPermission returns true when at least one permission matches", () => {
    const userSessionDetails = {
      roles: [Roles.USER_ACCOUNT_ADMIN],
    };
    expect(
      hasAnyPermission(userSessionDetails, [
        PERMISSIONS.RESULTS_ENTER,
        PERMISSIONS.USER_READ,
      ]),
    ).toBeTruthy();
  });
});

