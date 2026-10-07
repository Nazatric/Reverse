declare module "jsfxr" {
  const sfxr: {
    generate(preset: string | Record<string, unknown>): HTMLAudioElement;
    toAudio(code: string): HTMLAudioElement;
    play(sound: HTMLAudioElement, volume?: number): HTMLAudioElement;
  };
  export default sfxr;
}
