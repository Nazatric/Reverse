export type NodeId = "music" | "games" | "homies" | "config" | "account";

/**
 * Positions are percentages of the stage; `d` and `ly` are in reference pixels (736-unit canvas),
 * measured from the original artwork:
 *  - d  = orb diameter
 *  - ly = distance from the orb's bottom edge to the centre of its label
 */
export interface HubNode {
  id: NodeId;
  label: string;
  x: number;
  y: number;
  d: number;
  ly: number;
}

export const HUB = { x: 49.5, y: 52, d: 172 };

export const NODES: HubNode[] = [
  { id: "music", label: "music", x: 18.5, y: 22.8, d: 172, ly: 23 },
  { id: "games", label: "games", x: 74.6, y: 31.1, d: 108, ly: 14 },
  { id: "config", label: "config", x: 77.6, y: 65.5, d: 108, ly: 24 },
  { id: "homies", label: "homies", x: 27.2, y: 73.8, d: 106, ly: 26 },
  { id: "account", label: "account", x: 53, y: 79.5, d: 94, ly: 16 },
];
