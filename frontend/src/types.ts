export interface User {
  id: string;
  username: string;
  email: string;
  createdAt: string;
}
export interface BaseStats {
  speed: number | null;
  acceleration: number | null;
  handling: number | null;
  power: number | null;
  boost: number | null;
}
export interface BuildStatsResult extends BaseStats {
  character: BaseStats;
  machine: BaseStats;
}
export interface StatsCatalog {
  gameVersionId: string;
  racers: Record<string, BaseStats>;
  machineParts: Record<string, BaseStats>;
  machines: Record<string, BaseStats>;
}
export type RacingType =
  | "SPEED"
  | "ACCELERATION"
  | "HANDLING"
  | "POWER"
  | "BOOST";
export interface Racer {
  id: string;
  name: string;
  racingType: RacingType | null;
  imagePath: string | null;
}
export interface Machine {
  id: string;
  name: string;
  racingType: RacingType | null;
  imagePath: string | null;
}
export interface Gadget {
  id: string;
  name: string;
  description: string | null;
  slotCost: number | null;
  imagePath: string | null;
}
export type MachinePartType = "FRONT" | "REAR" | "TIRE";
export interface MachinePart {
  id: string;
  type: MachinePartType;
  sourceMachineId: string;
  sourceMachineName: string;
  sourceMachineImagePath: string | null;
  racingType: RacingType | null;
}
export interface GameVersion {
  id: string;
  version: string;
  releasedAt: string;
}
export interface RemixSource {
  id: string;
  title: string;
}
export interface Build {
  id: string;
  title: string;
  description: string;
  author: Pick<User, "id" | "username">;
  racer: Racer;
  frontPart: MachinePart;
  rearPart: MachinePart;
  tirePart: MachinePart | null;
  gameVersion: GameVersion | null;
  remixedFrom: RemixSource | null;
  gadgets: Gadget[];
  createdAt: string;
  updatedAt: string;
  score: number;
  upvotes: number;
  downvotes: number;
}
export interface BuildDraft {
  title: string;
  description: string;
  racerId: string;
  frontPartId: string;
  rearPartId: string;
  tirePartId: string | null;
  machineType: RacingType | null;
  gameVersionId: string | null;
  remixedFromBuildId: string | null;
  gadgetIds: string[];
}
export interface TopCommunitySnapshot {
  schemaVersion: number;
  ranking: "best-rated";
  scope: "overall";
  patches: "all";
  snapshotAt: string;
  items: {
    rank: number;
    build: Build;
    machineType: RacingType;
    stats: BuildStatsResult;
    buildUrl: string;
    artworkUrl: string | null;
  }[];
}
export interface BuildPage {
  items: Build[];
  total: number;
  page: number;
  size: number;
}
export interface Comment {
  id: string;
  buildId: string;
  authorId: string;
  author: string;
  text: string;
  createdAt: string;
}
export interface CommentPage {
  items: Comment[];
  total: number;
  page: number;
  size: number;
}
export interface Vote {
  score: number;
  upvotes: number;
  downvotes: number;
  myVote: number;
}
export interface Session {
  token: string;
  user: User;
}
export interface GameNewsItem {
  id: string;
  title: string;
  url: string;
  publishedAt: string;
}
