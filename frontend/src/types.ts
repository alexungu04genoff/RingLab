export interface User {
  id: string;
  username: string;
  email: string;
  createdAt: string;
}
export interface GameItem {
  id: string;
  name: string;
  racingType: string | null;
  description: string | null;
  slotCost: number | null;
  imagePath: string | null;
}
export interface Build {
  id: string;
  title: string;
  description: string;
  author: Pick<User, "id" | "username">;
  racer: GameItem;
  machine: GameItem;
  gadgets: GameItem[];
  createdAt: string;
  updatedAt: string;
  score: number;
}
export interface BuildDraft {
  title: string;
  description: string;
  racerId: string;
  machineId: string;
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
export interface Vote {
  score: number;
  myVote: number;
}
export interface Session {
  token: string;
  user: User;
}
