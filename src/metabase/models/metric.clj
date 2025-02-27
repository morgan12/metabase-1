(ns metabase.models.metric
  "A Metric is a saved MBQL 'macro' expanding to a combination of `:aggregation` and/or `:filter` clauses.
  It is passed in as an `:aggregation` clause but is replaced by the `expand-macros` middleware with the appropriate
  clauses."
  (:require
   [clojure.set :as set]
   [medley.core :as m]
   [metabase.lib.core :as lib]
   [metabase.lib.metadata :as lib.metadata]
   [metabase.lib.metadata.jvm :as lib.metadata.jvm]
   [metabase.lib.metadata.protocols :as lib.metadata.protocols]
   [metabase.lib.query :as lib.query]
   [metabase.lib.schema.common :as lib.schema.common]
   [metabase.mbql.util :as mbql.u]
   [metabase.models.interface :as mi]
   [metabase.models.revision :as revision]
   [metabase.models.serialization :as serdes]
   [metabase.util :as u]
   [metabase.util.i18n :refer [tru]]
   [metabase.util.malli :as mu]
   [methodical.core :as methodical]
   [toucan.models :as models]
   [toucan2.core :as t2]
   [toucan2.tools.hydrate :as t2.hydrate]))

(models/defmodel Metric :metric)

(doto Metric
  (derive ::mi/read-policy.full-perms-for-perms-set)
  (derive ::mi/write-policy.superuser)
  (derive ::mi/create-policy.superuser))

(defn- pre-update [{:keys [creator_id id], :as updates}]
  (u/prog1 updates
    ;; throw an Exception if someone tries to update creator_id
    (when (contains? updates :creator_id)
      (when (not= creator_id (t2/select-one-fn :creator_id Metric :id id))
        (throw (UnsupportedOperationException. (tru "You cannot update the creator_id of a Metric.")))))))

(defmethod mi/perms-objects-set Metric
  [metric read-or-write]
  (let [table (or (:table metric)
                  (t2/select-one ['Table :db_id :schema :id] :id (u/the-id (:table_id metric))))]
    (mi/perms-objects-set table read-or-write)))

(mi/define-methods
 Metric
 {:types      (constantly {:definition :metric-segment-definition})
  :properties (constantly {::mi/timestamped? true
                           ::mi/entity-id    true})
  :pre-update pre-update})

(mu/defn ^:private definition-description :- [:maybe ::lib.schema.common/non-blank-string]
  "Calculate a nice description of a Metric's definition."
  [metadata-provider :- lib.metadata/MetadataProvider
   {:keys [definition], table-id :table_id, :as _metric}]
  (when (seq definition)
    (when-let [{database-id :db_id} (lib.metadata.protocols/table metadata-provider table-id)]
      (let [query (lib.query/query-from-legacy-inner-query metadata-provider database-id definition)]
        (lib/describe-query query)))))

(defn- warmed-metadata-provider [metrics]
  (let [metadata-provider (doto (lib.metadata.jvm/application-database-metadata-provider)
                            (lib.metadata.protocols/store-metadatas! :metadata/metric metrics))
        segment-ids       (into #{} (mbql.u/match (map :definition metrics)
                                      [:segment (id :guard integer?) & _]
                                      id))
        segments          (lib.metadata.protocols/bulk-metadata metadata-provider :metadata/segment segment-ids)
        field-ids         (mbql.u/referenced-field-ids (into []
                                                             (comp cat (map :definition))
                                                             [metrics segments]))
        fields            (lib.metadata.protocols/bulk-metadata metadata-provider :metadata/field field-ids)
        table-ids         (into #{}
                                (comp cat (map :table_id))
                                [fields segments metrics])]
    ;; this is done for side-effects
    (lib.metadata.protocols/bulk-metadata metadata-provider :metadata/table table-ids)
    metadata-provider))

(methodical/defmethod t2.hydrate/batched-hydrate [Metric :definition_description]
  [_model _key metrics]
  (let [metadata-provider (warmed-metadata-provider metrics)]
    (for [metric metrics]
      (assoc metric :definition_description (definition-description metadata-provider metric)))))


;;; --------------------------------------------------- REVISIONS ----------------------------------------------------

(defmethod revision/serialize-instance Metric
  [_model _id instance]
  (dissoc instance :created_at :updated_at))

(defmethod revision/diff-map Metric
  [model metric1 metric2]
  (if-not metric1
    ;; model is the first version of the metric
    (m/map-vals (fn [v] {:after v}) (select-keys metric2 [:name :description :definition]))
    ;; do our diff logic
    (let [base-diff ((get-method revision/diff-map :default)
                     model
                     (select-keys metric1 [:name :description :definition])
                     (select-keys metric2 [:name :description :definition]))]
      (cond-> (merge-with merge
                          (m/map-vals (fn [v] {:after v}) (:after base-diff))
                          (m/map-vals (fn [v] {:before v}) (:before base-diff)))
        (or (get-in base-diff [:after :definition])
            (get-in base-diff [:before :definition])) (assoc :definition {:before (get-in metric1 [:definition])
                                                                          :after  (get-in metric2 [:definition])})))))


;;; ------------------------------------------------- SERIALIZATION --------------------------------------------------

(defmethod serdes/hash-fields Metric
  [_metric]
  [:name (serdes/hydrated-hash :table) :created_at])

(defmethod serdes/extract-one "Metric"
  [_model-name _opts metric]
  (-> (serdes/extract-one-basics "Metric" metric)
      (update :table_id   serdes/export-table-fk)
      (update :creator_id serdes/export-user)
      (update :definition serdes/export-mbql)))

(defmethod serdes/load-xform "Metric" [metric]
  (-> metric
      serdes/load-xform-basics
      (update :table_id   serdes/import-table-fk)
      (update :creator_id serdes/import-user)
      (update :definition serdes/import-mbql)))

(defmethod serdes/dependencies "Metric" [{:keys [definition table_id]}]
  (into [] (set/union #{(serdes/table->path table_id)}
                      (serdes/mbql-deps definition))))

(defmethod serdes/storage-path "Metric" [metric _ctx]
  (let [{:keys [id label]} (-> metric serdes/path last)]
    (-> metric
        :table_id
        serdes/table->path
        serdes/storage-table-path-prefix
        (concat ["metrics" (serdes/storage-leaf-file-name id label)]))))
