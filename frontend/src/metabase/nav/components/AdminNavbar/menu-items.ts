import { User } from "metabase-types/api";
import { PLUGIN_ADMIN_NAV_ITEMS } from "metabase/plugins";
import { t } from "ttag";

export const NAV_PERMISSION_GUARD: Record<string, (user: User) => boolean> = {};

const defaultGuard = (user: User) => user.is_superuser;

const canAccessMenuItem = (key: string, user: User) => {
  return defaultGuard(user) || NAV_PERMISSION_GUARD[key]?.(user);
};

const MENU_ITEMS = [
  {
    name: t`Settings`,
    path: "/admin/settings",
    key: "settings",
  },
  {
    name: t`People`,
    path: "/admin/people",
    key: "people",
  },
  {
    name: t`Data Model`,
    path: "/admin/data-model",
    key: "data-model",
  },
  {
    name: t`Databases`,
    path: "/admin/databases",
    key: "databases",
  },
  {
    name: t`Permissions`,
    path: "/admin/permissions",
    key: "permissions",
  },
  ...PLUGIN_ADMIN_NAV_ITEMS,
  {
    name: t`Troubleshooting`,
    path: "/admin/troubleshooting",
    key: "troubleshooting",
  },
];

export const getAllowedMenuItems = (user: User) =>
  MENU_ITEMS.filter(item => canAccessMenuItem(item.key, user));
