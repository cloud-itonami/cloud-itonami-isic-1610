(ns sawmilling.registry
  "Pure-function domain logic for the sawmill plant-operations
  coordination actor -- equipment/batch verification, shipment-volume
  recompute, lumber-grade validation, moisture-content plausibility
  validation, and draft maintenance-schedule/shipment-coordination
  record construction.

  Per docs/adr/0001-architecture.md Decision 1: this vertical has NO
  pre-existing `kotoba-lang/sawmilling`-style capability library to
  wrap (verified: no such repo exists). The domain logic therefore
  lives here as pure functions, re-verified INDEPENDENTLY by
  `sawmilling.governor` -- the same 'ground truth, not self-report'
  discipline every sibling actor's own registry establishes (e.g.
  `logging.registry/permit-allowance-exceeded?`,
  `chemmineops.registry/royalty-matches-claim?`): never trust a
  proposal's own self-reported volume/status when the inputs needed to
  recompute it independently are already on record.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real mill-operations system. It builds the DRAFT record
  a plant coordinator would keep (a scheduled maintenance window, a
  coordinated shipment), not the act of actuating saw/planer/kiln
  equipment or dispatching a real freight carrier (this actor NEVER
  does either -- see README `What this actor does NOT do`).")

;; ----------------------------- constants -----------------------------

(def valid-grades
  "The closed set of lumber-grade values a production-batch record may
  declare (softwood dimension-lumber grading, NLGA-informed). Anything
  else is a fabricated/unrecognized grade -- the governor HARD-holds
  rather than let an invented grade pass through."
  #{:select-structural :no1 :no2 :no3 :stud :construction :standard :economy})

(def moisture-content-min-percent
  "Physical floor for a lumber moisture-content reading (oven-dry
  wood approaches, but never reaches, 0%)."
  0.0)

(def moisture-content-max-percent
  "Physical ceiling for a lumber moisture-content reading. Green
  softwood moisture content is expressed as a percentage of oven-dry
  weight and can exceed 100% (fiber-saturation point is ~30%, but
  green sapwood in species like western redcedar can carry well over
  200% of its dry weight in water). A reading above this is
  implausible sensor/scale data, not a real batch."
  250.0)

;; ----------------------------- equipment checks -----------------------------

(defn equipment-verified?
  "Ground-truth check: has `equipment`'s own record been marked
  verified (i.e. it has actually been inspected/commissioned and
  registered in the SSoT, not merely referenced from an unverified
  maintenance request)? A pure predicate over the equipment's own
  permanent field -- no proposal inspection needed."
  [equipment]
  (true? (:verified? equipment)))

(defn equipment-registered?
  "Ground-truth check: does `equipment`'s own record carry a
  `:registered?` true flag (i.e. it is on file in the plant's
  equipment registry)? Scheduling maintenance against equipment that
  is not on file and registered is the exact scope violation this
  actor's HARD invariant ('plant/batch record must be independently
  verified/registered before any action') exists to block."
  [equipment]
  (true? (:registered? equipment)))

(defn equipment-ready?
  "Combined ground-truth gate: the equipment must be both `verified?`
  AND `registered?` before ANY maintenance may be scheduled against
  it. Two independent facts on the equipment's own permanent record,
  neither inferred from the advisor's own rationale."
  [equipment]
  (and (equipment-verified? equipment) (equipment-registered? equipment)))

;; ----------------------------- batch checks -----------------------------

(defn batch-verified?
  "Ground-truth check: has `batch`'s own record been marked verified
  (i.e. its grade/volume/moisture-content claims have actually been
  QC-inspected, not merely logged from an unverified intake patch)?"
  [batch]
  (true? (:verified? batch)))

(defn batch-registered?
  "Ground-truth check: is `batch`'s own record on file in the plant's
  production ledger? Coordinating a shipment against a batch that is
  not on file and registered is the exact scope violation this
  actor's HARD invariant ('plant/batch record must be independently
  verified/registered before any action') exists to block."
  [batch]
  (true? (:registered? batch)))

(defn batch-ready?
  "Combined ground-truth gate: the batch must be both `verified?` AND
  `registered?` before ANY shipment may be coordinated against it."
  [batch]
  (and (batch-verified? batch) (batch-registered? batch)))

(defn shipment-volume-exceeded?
  "Ground-truth check for a `:coordinate-shipment` proposal:
  would `shipped-to-date-board-ft` + `new-volume-board-ft` exceed
  `batch`'s own recorded `:volume-board-ft` (the batch's own logged
  production volume)? Needs no proposal inspection or stored-verdict
  lookup -- its inputs are permanent fields already on the batch's
  own permit record, the same shape every sibling actor's own cost/
  total-matching check uses."
  [batch new-volume-board-ft]
  (let [capacity (:volume-board-ft batch)
        so-far (:shipped-volume-board-ft batch 0.0)]
    (and (number? capacity)
         (number? new-volume-board-ft)
         (> (+ (double so-far) (double new-volume-board-ft)) (double capacity)))))

(defn grade-valid?
  "Is `grade` one of the closed, known lumber-grade values? nil/blank
  is treated as invalid (a production-batch patch must declare a real
  grade, not omit it silently)."
  [grade]
  (contains? valid-grades grade))

(defn moisture-content-valid?
  "Is `percent` a physically plausible lumber moisture-content
  reading? Rejects nil, non-numbers, negative values, and values
  beyond `moisture-content-max-percent` -- a fabricated or
  sensor-error reading, never let through as a real batch fact."
  [percent]
  (and (number? percent)
       (>= (double percent) moisture-content-min-percent)
       (<= (double percent) moisture-content-max-percent)))

;; ----------------------------- draft record construction -----------------------------

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the human plant supervisor's/shipping approver's act, not this
  actor's."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-maintenance
  "Validate + construct the MAINTENANCE-SCHEDULE DRAFT -- a proposed
  saw-blade/planer/kiln maintenance window against a verified,
  registered piece of equipment. Pure function -- does not actuate
  saw/planer/kiln equipment or execute any maintenance; it builds the
  RECORD a plant coordinator would keep. `sawmilling.governor`
  independently re-verifies the equipment's own verified/registered
  ground truth, and permanently blocks any attempt to set
  `:finalize? true` on a kiln schedule (see README `Actuation`),
  before this is ever allowed to commit."
  [maintenance-id equipment-id sequence]
  (when-not (and maintenance-id (not= maintenance-id ""))
    (throw (ex-info "maintenance: maintenance_id required" {})))
  (when-not (and equipment-id (not= equipment-id ""))
    (throw (ex-info "maintenance: equipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "maintenance: sequence must be >= 0" {})))
  (let [maintenance-number (str "MNT-" (zero-pad sequence 6))
        record {"record_id" maintenance-number
                "kind" "maintenance-schedule-draft"
                "maintenance_id" maintenance-id
                "equipment_id" equipment-id
                "immutable" true}]
    {"record" record "maintenance_number" maintenance-number
     "certificate" (unsigned-certificate "MaintenanceSchedule" maintenance-number maintenance-number)}))

(defn register-shipment
  "Validate + construct the SHIPMENT-COORDINATION DRAFT -- a proposed
  outbound lumber shipment against a verified, registered production
  batch. Pure function -- does not dispatch any real freight carrier;
  it builds the RECORD a plant coordinator would keep.
  `sawmilling.governor` independently re-verifies the shipment's own
  claimed volume against `shipment-volume-exceeded?`, before this is
  ever allowed to commit."
  [shipment-id sequence]
  (when-not (and shipment-id (not= shipment-id ""))
    (throw (ex-info "shipment: shipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "shipment: sequence must be >= 0" {})))
  (let [shipment-number (str "SHP-" (zero-pad sequence 6))
        record {"record_id" shipment-number
                "kind" "shipment-coordination-draft"
                "shipment_id" shipment-id
                "immutable" true}]
    {"record" record "shipment_number" shipment-number
     "certificate" (unsigned-certificate "ShipmentCoordination" shipment-number shipment-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
