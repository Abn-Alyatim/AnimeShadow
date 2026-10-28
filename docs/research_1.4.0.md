# AnimeShadow 1.4.0: research on watching and reading sources
Researched Oct 28, 2026 for Madyan. Rule: nothing pirated. Only sources that legally hold the rights, or a link to the rights-holder's own official app or site.

## TL;DR (الخلاصة)
- **Watching:** no licensed anime service publishes an open API that lets a third-party app play its episodes inside that app. Crunchyroll, Netflix, Prime, Bilibili TV, Ani-One and Muse Asia all lack one. The legal route is official links: AniList already lists, for every series, the official streaming pages (`externalLinks` of type STREAMING) and the official trailer. Those pages include free official YouTube playlists from Muse Asia, Ani-One and the studio channels.
  → **Shipped in 1.4.0:** a «شاهد» section on the anime page. It shows the official trailer (YouTube thumbnail; tap opens YouTube), «حلقات مجانية رسمية على يوتيوب» (the official YouTube playlist/channel when AniList lists one) and the other official platforms (Crunchyroll, Netflix, …). Every item opens the official app or site. Needs no key and no server.
- **Reading manga:** no source that is both legal and has a usable API allows in-app chapter reading for an app that shows ads. MangaDex forbids ads and its chapters are mostly scanlations. MANGA Plus has no public API and its terms forbid reverse engineering. Comick was a piracy site and shut down (Sep 2025).
  → **Decision:** the in-app chapter reader keeps «قريباً» (the pluggable provider stays empty). **Shipped instead:** «اقرأ رسمياً» on the manga page. It lists the official readers AniList lists (MANGA Plus, VIZ, Shonen Jump+, K MANGA, …), and each opens the official app or site.

## 1. Anime: legal ways to watch
| Source | Legal? | Public API usable by our app? | Verdict |
|---|---|---|---|
| **AniList `externalLinks` + `trailer` + `streamingEpisodes`** | Yes. Metadata pointing to the official pages | Yes. Same free GraphQL API the app already uses | **Used.** Example: Frieren lists Crunchyroll, Netflix, Hulu, Prime and the official YouTube playlist `PLwLSw1_eDZl10YPPR7qDsVf10wZfYnIMK`; Re:Zero lists YouTube playlist + @Rezero_official. https://anilist.gitbook.io/anilist-apiv2-docs/ |
| **Muse Asia (YouTube)** | Yes, licensed (Muse Communication is the rights agent for 30+ anime/manga copyright partners) | No API of its own. Videos are reachable through YouTube | **Linked** (through AniList's YouTube links). Geo-restricted: lineups vary by region (mostly South/Southeast Asia). https://www.youtube.com/c/MuseAsia · https://www.reddit.com/r/anime/comments/1agbbxs/ |
| **Ani-One Asia (YouTube)** | Yes, licensed | No API of its own | **Linked.** Many titles are available only in SG/MY/TH/ID/PH/VN/BN/KH/LA/MM. https://www.youtube.com/c/AniOneAsia |
| **YouTube Data API / IFrame player** | Yes. Embedding official videos is allowed by YouTube's terms | Yes, but needs an API key, quota, and compliance with YouTube's "Required Minimum Functionality" (identification through the Referer etc.) | Not needed for 1.4.0: AniList already gives the trailer id and playlist links, and opening YouTube directly avoids key, quota and policy risk. Possible later: an in-app episode list for official playlists through the server, with a key kept on the server. https://developers.google.com/youtube/terms/developer-policies · https://developers.google.com/youtube/terms/required-minimum-functionality |
| Crunchyroll / Netflix / Prime / Bilibili TV / Hulu | Yes | **No public developer API** for playback | Linked only (open their app/page) |
| Pirate sites / scrapers (Gogoanime-type, "consumet" scrapers, …) | **No** | — | Rejected (Madyan's no-piracy rule) |

Note on regions: Madyan is in Yemen. Free official YouTube anime (Muse Asia / Ani-One) is region-locked per title, so some links may say "not available in your country". That limit comes from the licensor, and the app can't change it legally.

## 2. Manga: legal chapter sources
| Source | Legal status | API & terms | Verdict |
|---|---|---|---|
| **MangaDex API** | Hosts mostly **scanlations** (fan translations without a license). A few official publisher uploads | Acceptable Usage Policy: "You MUST credit MangaDex", "You MUST credit scanlation groups…", "**You CANNOT run ads or paid services on your website and/or apps**", "the point is that you must not be profiting from our API". https://api.mangadex.org/docs/ | **Rejected.** AnimeShadow shows AdMob ads (policy violation), and the scanlated chapters are not licensed. |
| **MANGA Plus by SHUEISHA** | Official and free (Shueisha) | **No public API.** Terms of Use Art. 9 prohibit "use for business purpose… duplication… other than the User's private use", "(3) Segregating a program or part of a program", "(4) **Reverse engineering, decompilation or disassemble**". Unofficial clients (e.g. github.com/hyugogirubato/mangaplus) are reverse-engineered. https://mangaplus.shueisha.co.jp/static/terms/eng/ | Not usable inside the app. **Linked** (open the MANGA Plus app/site; AniList gives the exact title URL, e.g. One Piece → https://mangaplus.shueisha.co.jp/titles/100020). |
| **Comick (comick.io)** | Piracy aggregator. **Shut down Sep 16, 2025** after DMCA pressure | — | Rejected. https://animecorner.me/comick-shut-down-manga-piracy-site/ |
| **K MANGA (Kodansha)** | Official (latest chapters free) | No public API | Linked when AniList lists it. https://kmanga.kodansha.com/ |
| **VIZ / Shonen Jump** | Official | No public API | Linked |
| **WEBTOON / Canvas** | Official | No public third-party reading API | Linked when listed |
| Scanlation aggregators (MangaKakalot-type) | **No** | — | Rejected |

**Verdict:** no source can be called safe for an in-app chapter reader in an app with ads. The app keeps its pluggable `ChapterProvider` (reader, downloads and offline library are ready), and «قريباً» stays until a publisher grants a license or offers an official API. The reading button that works today is «اقرأ رسمياً», which opens the official reader.

## 3. AI for «شادو سينباي»
- Groq (key in `ANIMESHADOW_GROQ_API_KEY`, server only): text model `openai/gpt-oss-120b`; images use `qwen/qwen3.8-27b`, which is now Groq's vision model. Llama 4 Scout was deprecated (shutdown Jul 17, 2026). https://console.groq.com/docs/vision · https://console.groq.com/docs/deprecations
- Optional fallback: Gemini `gemini-3.8-flash` (`ANIMESHADOW_GEMINI_API_KEY`).
- Checked live on Oct 28 with the same prompt on gpt-oss-120b: Arabic recommendations from the user's list with tappable cards, polite refusal of an off-topic maths question, refusal to be renamed («اسمي شادو سينباي ولا يمكن تغييره»), and ratings phrased as personal opinion.
- The image path was tested with a mocked provider only. Every vision provider reachable from this box was out of credit or not deployed, so the first real image test happens once Madyan's Groq key is set.
