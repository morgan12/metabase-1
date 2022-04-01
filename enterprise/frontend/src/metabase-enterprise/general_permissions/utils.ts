import { User } from "metabase-types/api";

export const canAccessMonitoringItems = (user?: User) =>
  user?.can_access_monitoring ?? false;
