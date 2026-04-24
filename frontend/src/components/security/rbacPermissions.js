import { Roles } from "../utils/Utils";

export const PERMISSIONS = {
  PATIENT_READ: "patient.read",
  ORDER_CREATE: "order.create",
  ORDER_READ: "order.read",
  RESULTS_ENTER: "results.enter",
  RESULTS_VALIDATE: "results.validate",
  REPORT_READ: "report.read",
  AUDIT_READ: "audit.read",
  USER_READ: "user.read",
  USER_WRITE: "user.write",
  SYSTEM_ADMIN: "system.admin",
};

const ROLE_PERMISSIONS = {
  [Roles.GLOBAL_ADMIN]: Object.values(PERMISSIONS),
  [Roles.AUDIT_TRAIL]: [PERMISSIONS.AUDIT_READ],
  [Roles.USER_ACCOUNT_ADMIN]: [PERMISSIONS.USER_READ, PERMISSIONS.USER_WRITE],
  [Roles.RECEPTION]: [
    PERMISSIONS.PATIENT_READ,
    PERMISSIONS.ORDER_CREATE,
    PERMISSIONS.ORDER_READ,
  ],
  [Roles.RESULTS]: [PERMISSIONS.ORDER_READ, PERMISSIONS.RESULTS_ENTER],
  [Roles.VALIDATION]: [PERMISSIONS.ORDER_READ, PERMISSIONS.RESULTS_VALIDATE],
  [Roles.REPORTS]: [PERMISSIONS.REPORT_READ],
};

const toRoleArray = (roleValue) =>
  Array.isArray(roleValue) ? roleValue : roleValue ? [roleValue] : [];

const toPermissionSet = (roles = []) => {
  const permissions = new Set();
  roles.forEach((role) => {
    const mappedPermissions = ROLE_PERMISSIONS[role] || [];
    mappedPermissions.forEach((permission) => permissions.add(permission));
  });
  return permissions;
};

const normalizeLabKey = (labName) =>
  !labName || labName === "AllLabUnits" ? "*" : labName;

export const buildPermissionContext = (userSessionDetails = {}) => {
  const globalPermissions = toPermissionSet(userSessionDetails.roles || []);
  const labPermissions = new Map();
  const labRoleMap = userSessionDetails.userLabRolesMap || {};

  Object.entries(labRoleMap).forEach(([labName, roles]) => {
    labPermissions.set(normalizeLabKey(labName), toPermissionSet(roles));
  });

  return { globalPermissions, labPermissions };
};

export const hasPermission = (userSessionDetails, permission, labName) => {
  if (!permission) {
    return true;
  }
  const { globalPermissions, labPermissions } =
    buildPermissionContext(userSessionDetails);

  if (globalPermissions.has(permission)) {
    return true;
  }

  const allLabs = labPermissions.get("*");
  if (allLabs?.has(permission)) {
    return true;
  }

  if (labName) {
    const directLabPermissions = labPermissions.get(labName);
    if (directLabPermissions?.has(permission)) {
      return true;
    }
  }

  return false;
};

export const hasAnyPermission = (
  userSessionDetails,
  permissions = [],
  labName,
) => permissions.some((permission) => hasPermission(userSessionDetails, permission, labName));

export const mapLegacyRoleRequirementsToPermissions = ({
  role,
  labUnitRole,
} = {}) => {
  const permissions = new Set();

  toRoleArray(role).forEach((legacyRole) => {
    (ROLE_PERMISSIONS[legacyRole] || []).forEach((permission) =>
      permissions.add(permission),
    );
  });

  if (labUnitRole) {
    Object.values(labUnitRole).forEach((roles) => {
      toRoleArray(roles).forEach((legacyRole) => {
        (ROLE_PERMISSIONS[legacyRole] || []).forEach((permission) =>
          permissions.add(permission),
        );
      });
    });
  }

  return Array.from(permissions);
};

