/**
 * Ready-to-install plugins. Each is a plain JSON document — the same format users
 * can paste or host themselves — kept here so the plugin system is usable out of the box.
 */
export interface ExamplePlugin {
  id: string;
  name: string;
  description: string;
  source: string;
}

const chill = {
  id: "example.chill",
  name: "Chill Chrome",
  version: "1.0.0",
  author: "The Gadget",
  description: "Cools the chrome and dims the glow a little.",
  theme: {
    accent: "#62BDF1",
    orb: ["#E0F5FF", "#8EC4E8", "#315A78", "#0A1B2C"],
    glow: 0.7,
  },
};

const gold = {
  id: "example.gold",
  name: "Gold Chrome",
  version: "1.0.0",
  author: "The Gadget",
  description: "Warm gold orbs and a warmer accent.",
  theme: {
    accent: "#FFC94D",
    orb: ["#FFE59B", "#C08A32", "#573614", "#1A1308"],
    glow: 1.1,
  },
};

const arcade = {
  id: "example.arcade",
  name: "Arcade Shelf",
  version: "1.0.0",
  author: "The Gadget",
  description: "Adds a hub orb with shortcuts to your library and 2048.",
  nodes: [{ id: "arcade.shelf", label: "arcade", x: 9, y: 46, d: 96, ly: 16, icon: "games", page: "games" }],
  pages: [
    {
      id: "arcade.page",
      label: "arcade",
      title: "Arcade",
      subtitle: "Quick links around your library",
      blocks: [
        { type: "text", value: "A small page added by a plugin. It opens your library, the player and 2048." },
        {
          type: "tiles",
          items: [
            { label: "2048", icon: "star", page: "2048" },
            { label: "albums", icon: "note", page: "albums" },
            { label: "now playing", icon: "music", page: "now" },
          ],
        },
        { type: "button", label: "open games", page: "games" },
      ],
    },
  ],
};

const links = {
  id: "example.links",
  name: "Quick Links",
  version: "1.0.0",
  author: "The Gadget",
  description: "Adds an orb that opens a page of your own links.",
  nodes: [{ id: "links.orb", label: "links", x: 91, y: 47, d: 88, ly: 16, icon: "star", page: "links.page" }],
  pages: [
    {
      id: "links.page",
      label: "links",
      title: "Quick links",
      subtitle: "Edit this file to change where these go",
      blocks: [
        { type: "link", label: "open example.com", url: "https://example.com" },
        { type: "note", value: "Buttons with a url open in a new tab. Buttons with a page stay in the app." },
      ],
    },
  ],
};

const toSource = (value: unknown) => JSON.stringify(value, null, 2);

export const EXAMPLES: ExamplePlugin[] = [
  { id: chill.id, name: chill.name, description: chill.description, source: toSource(chill) },
  { id: gold.id, name: gold.name, description: gold.description, source: toSource(gold) },
  { id: arcade.id, name: arcade.name, description: arcade.description, source: toSource(arcade) },
  { id: links.id, name: links.name, description: links.description, source: toSource(links) },
];

export const PLUGIN_TEMPLATE = toSource({
  id: "my.plugin",
  name: "My Plugin",
  version: "1.0.0",
  author: "me",
  description: "What this plugin changes.",
  theme: { accent: "#E9F33F", orbScale: 1.05, glow: 1.1 },
  nodes: [{ id: "my.orb", label: "my orb", x: 10, y: 46, d: 96, ly: 16, icon: "star", page: "my.page" }],
  pages: [
    {
      id: "my.page",
      label: "my orb",
      title: "My page",
      subtitle: "Built by a plugin",
      blocks: [
        { type: "header", value: "Hello" },
        { type: "text", value: "Anything described here is rendered by the app." },
        { type: "button", label: "open the player", page: "now" },
        { type: "link", label: "open a website", url: "https://example.com" },
      ],
    },
  ],
});
