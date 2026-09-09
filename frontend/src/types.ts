export interface User {
  id: string;
  username: string;
  email: string;
  createdAt: string;
}
export interface Racer {
  id: string;
  name: string;
  racingType: string;
  imagePath: string | null;
}
export interface Machine {
  id: string;
  name: string;
  racingType: string;
  imagePath: string | null;
}
export interface Gadget {
  id: string;
  name: string;
  description: string | null;
  slotCost: number | null;
  imagePath: string | null;
}
export interface Build {
  id: string;
  title: string;
  description: string;
  author: Pick<User, "id" | "username">;
  racer: Racer;
  machine: Machine;
  gadgets: Gadget[];
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
