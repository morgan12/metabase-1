import { hasPremiumFeature } from "metabase-enterprise/settings";
import { PLUGIN_FEATURE_LEVEL_PERMISSIONS } from "metabase/plugins";
import {
  canAccessSettings,
  canAccessDataModel,
  canAccessDatabaseManagement,
} from "./utils";

import { getFeatureLevelDataPermissions } from "./permissions";
import { DATA_COLUMNS } from "./constants";
import { NAV_PERMISSION_GUARD } from "metabase/nav/components/AdminNavbar";

if (hasPremiumFeature("advanced_permissions")) {
  NAV_PERMISSION_GUARD["data-model"] = canAccessDataModel;
  NAV_PERMISSION_GUARD["database"] = canAccessDatabaseManagement;

  PLUGIN_FEATURE_LEVEL_PERMISSIONS.canAccessSettings = canAccessSettings;
  PLUGIN_FEATURE_LEVEL_PERMISSIONS.canAccessDataModel = canAccessDataModel;
  PLUGIN_FEATURE_LEVEL_PERMISSIONS.canAccessDatabaseManagement = canAccessDatabaseManagement;

  PLUGIN_FEATURE_LEVEL_PERMISSIONS.getFeatureLevelDataPermissions = getFeatureLevelDataPermissions;
  PLUGIN_FEATURE_LEVEL_PERMISSIONS.dataColumns = DATA_COLUMNS;
}
