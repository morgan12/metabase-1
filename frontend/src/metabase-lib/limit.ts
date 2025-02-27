import * as ML from "cljs/metabase.lib.limit";
import type { Query, Limit } from "./types";

export function currentLimit(query: Query): Limit {
  return ML.current_limit(query);
}

export function limit(query: Query, limit: Limit): Query {
  return ML.limit(query, limit);
}

export function hasLimit(query: Query) {
  const limit = currentLimit(query);
  return typeof limit === "number" && limit > 0;
}
