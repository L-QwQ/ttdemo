export interface LotteryType {
  id: number
  code: string
  name: string
  category: number
  drawWeekdays: string | null
  drawTimeOfDay: string | null
  saleEndOffsetMin: number
  playConfig: string | null
  sortNo: number
  status: number
}

export interface Issue {
  id: number
  typeCode: string
  issueNo: string
  drawTime: string
  saleEndTime: string
  redNumbers: string | null
  blueNumbers: string | null
  extra: string | null
  drawStatus: number
  drawStatusName: string
  settleStatus: number
  saleEnded: boolean
  remainSeconds: number
  dataSource: number
}

export interface PriceBreakdown {
  base: number
  coefficient: number
  text: string
}

export interface CreatorBrief {
  id: number
  penName: string
  avatar: string | null
  accuracyScore: number
  sampleSize: number
  currentHitStreak: number
  level: number
}

export interface FreeItem {
  itemType: string
  /** 创作者填的展示文案，可能为空，前端按 itemType 兜底 */
  label: string | null
  /** 号码值，仅免费项下发 */
  value: string | null
}

export interface PredictionListItem {
  id: number
  predictionNo: string
  title: string
  playType: string
  playTypeName: string
  typeCode: string
  issueNo: string
  saleEndTime: string | null
  pricePoints: number
  isFree: boolean
  priceBreakdown: PriceBreakdown
  summary: string
  freeItems: FreeItem[]
  /** 预测项总数（含付费项），用于「还有 N 项待解锁」 */
  itemCount: number
  freeCount: number
  creator: CreatorBrief
  viewCount: number
  buyerCount: number
  isPurchased: boolean
  status: number
  statusName: string
  hitRate: number | null
  isHit: number | null
  createdAt: string
}

export interface ItemHit {
  itemType: string
  itemLabel: string | null
  itemValue: string
  hitResult: number | null
  hitDetail: string | null
}

export interface HitInfo {
  hitRate: number
  isHit: number
  hitCount: number
  itemCount: number
  items: ItemHit[]
}

/** 详情页的预测项：付费未解锁时 value 为 null，用 count 画占位球 */
export interface PredictionItemView {
  itemType: string
  label: string | null
  value: string | null
  count: number
  free: boolean
  locked: boolean
}

export interface PredictionDetail {
  id: number
  predictionNo: string
  title: string
  playType: string
  playTypeName: string
  typeCode: string
  issueId: number
  issueNo: string
  saleEndTime: string | null
  remainSeconds: number
  pricePoints: number
  isFree: boolean
  priceBreakdown: PriceBreakdown
  summary: string
  freeItems: FreeItem[]
  items: PredictionItemView[]
  contentHtml: string | null
  contentImages: string[]
  isPurchased: boolean
  isOwner: boolean
  itemsTotal: number
  itemsFree: number
  status: number
  statusName: string
  hitInfo: HitInfo | null
  creator: CreatorBrief
  viewCount: number
  buyerCount: number
  lockedHint: string
  auditRemark: string | null
  createdAt: string
}

export interface PriceQuote {
  base: number
  coefficient: number
  sampleFactor: number
  suggestPrice: number
  minPrice: number
  maxPrice: number
  accuracyScore: number
  sampleSize: number
  formula: string
  note: string
}

export interface PointAccount {
  available: number
  frozen: number
  totalRecharge: number
  totalIncome: number
  totalConsume: number
  totalExpired: number
  expiringSoon: { points: number; expireAt: string }
  canWithdraw: boolean
  notice: string
  rechargeRate: string
  expireMonths: number
}

export interface PointTransaction {
  id: number
  txnNo: string
  userId: number
  direction: number
  amount: number
  balanceBefore: number
  balanceAfter: number
  bizType: string
  bizNo: string
  remark: string | null
  createdAt: string
}

export interface CreatorProfile {
  id: number
  userId: number
  penName: string
  intro: string | null
  tags: string | null
  status: number
  statusName: string
  creatorLevel: number
  platformFeeRate: number
  favCount: number
  totalIncome: number
  totalPredictions: number
  accuracyScore: number
  sampleSize: number
  currentHitStreak: number
  avatar: string | null
  certifiedAt: string | null
  auditRemark: string | null
}

export interface CreatorDashboard {
  totalPredictions: number
  onlineCount: number
  settledCount: number
  hitCount: number
  hitRate: number
  totalIncome: number
  monthIncome: number
  totalBuyer: number
  totalView: number
  accuracyScore: number
  sampleSize: number
  currentHitStreak: number
}

export interface AccuracyInfo {
  typeCode: string
  score: number
  sampleSize: number
  rawHitRate: number
  weightedHitRate: number
  wilsonLower: number
  currentHitStreak: number
  maxHitStreak: number
  recent10Rate: number
  display: string
  calcNote: string
}

export interface RankRow {
  rankNo: number
  creatorId: number
  penName: string
  avatar: string | null
  level: number
  metricValue: number
  metricText: string
  sampleSize: number
  currentHitStreak: number
  totalBuyer: number
}

export interface RankData {
  rankType: string
  typeCode: string
  snapshotDate: string
  calcNote: string
  list: RankRow[]
}
