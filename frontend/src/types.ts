export interface User {
  id: string;
  username: string;
  email: string;
  createdAt: string;
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
  racingType: RacingType;
  imagePath: string | null;
}
export interface Machine {
  id: string;
  name: string;
  racingType: RacingType;
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
  racingType: RacingType;
}
export interface GameVersion {
  id: string;
  version: string;
  releasedAt: string;
}
export interface Build {
  id: string;
  title: string;
  description: string;
  author: Pick<User, "id" | "username">;
  racer: Racer;
  frontPart: MachinePart;
  rearPart: MachinePart;
  tirePart: MachinePart;
  gameVersion: GameVersion | null;
  gadgets: Gadget[];
  createdAt: string;
  updatedAt: string;
  score: number;
}
export interface BuildDraft {
  title: string;
  description: string;
  racerId: string;
  frontPartId: string;
  rearPartId: string;
  tirePartId: string;
  gameVersionId: string | null;
  gadgetIds: string[];
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
