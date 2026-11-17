"use strict";

// Static GitHub Pages demo — fetches pre-captured JSON from docs/data/
// (see scripts/capture_demo_snapshots.py) rather than embedding the data
// in this file or in index.html, so re-capturing never requires hand-editing
// any JS/HTML.

const STRATEGY_ORDER = ["NONE", "POPULARITY", "COLLABORATIVE", "BANDIT", "NEURAL", "DIVERSE_POPULARITY"];

// Kept in sync with web/src/utils/humanizeSource.ts's mapping — same badge
// language in both the live React app and this static demo.
const SOURCE_LABELS = {
  search: "Matched your search",
  none: "Baseline search result",
  popularity: "Boosted — popular with other shoppers",
  "collaborative filtering": "Boosted — shoppers like you also viewed this",
  "bandit exploration": "Surfaced for exploration",
  "neural ranking": "Ranked by the neural model",
  "popularity (diversified)": "Boosted for popularity, then spread across categories",
};

function humanizeSource(source) {
  return SOURCE_LABELS[(source || "").trim().toLowerCase()] ?? source;
}

// Offline evaluation numbers from RESULTS.md, regenerated via
// scripts/run-recommender-eval.sh. RESULTS.md reports THREE sections:
//   - "Implicit clickstream eval" — relevance grades derived from the same
//     synthetic clickstream the strategies themselves read from. Circular
//     ground truth (see RESULTS.md's own caveat), not the table to trust.
//   - "Independent WANDS relevance eval" — a second, ground-truth-independent
//     table scored against WANDS's own human relevance grades (label.csv),
//     which no strategy was trained or tuned against. This is the more
//     trustworthy of the two, and is what's shown below.
//   - "Off-policy (IPS) evaluation" — an inverse-propensity-weighted estimate
//     of each strategy's real reward, not shown per-card here (single-number
//     IPS estimates need the effective-sample-size caveat alongside them to
//     be honest — see RESULTS.md's own table and read-me-first paragraph
//     rather than a stripped-down version here).
// Also note: every query below is served by a widen-then-select pipeline —
// search-service is asked for a much wider candidate pool than what's
// displayed, a hard eligibility filter drops zero-social-proof candidates,
// and only the survivors are handed to whichever strategy runs — so results
// (including None's) reflect that selection stage, not just
// search-service's raw top-K.
// Update these four fields per strategy if RESULTS.md's numbers change.
const STRATEGY_INFO = {
  NONE: {
    label: "None",
    tagline: "The control group. This strategy changes nothing.",
    how: "This strategy sends the candidates from search-service through with no changes. It does not change their order. It does not add or remove products. Its function is to give a baseline for the other strategies.",
    differs: "This is the only strategy that changes nothing. The numbers of the other strategies have a meaning only when you compare them with this baseline. In the independent WANDS evaluation, no strategy is clearly better than this baseline.",
    grounding: "A control with no changes is standard practice in A/B tests and in offline evaluations. Without this control, you cannot know if a different strategy helped.",
    metrics: { ndcg: 0.9673, mrr: 0.9981, recall: 0.0608, precision: 0.9967 },
  },
  POPULARITY: {
    label: "Popularity",
    tagline: "Mixes search relevance with popularity. It can also add popular products that the search did not find.",
    how: "This strategy changes the order of the candidates with a 60/40 mix of two scores. The first score is the search-service score. The second score is a popularity score from the clickstream data. This score uses the counts of views, clicks, cart adds and purchases on a log scale. Then the strategy adds a maximum of 2 popular products that are not in the candidates. Thus it adds new products and does not only change the order.",
    differs: "This is the only strategy that uses only a global signal. Popularity is the same for all users, thus this strategy is not personalized. Of the six strategies, it costs the least and is the most stable. In the independent WANDS evaluation, it has the highest nDCG. But the difference is too small to be decisive.",
    grounding: "Popularity backfill is the most usual pattern in production recommender systems. See arXiv:2509.06002, “A Survey of Real-World Recommender Systems.” This survey shows popularity backfill as a standard, stable part of real candidate generation pipelines. It is not only a weak example for comparison.",
    metrics: { ndcg: 0.9721, mrr: 0.9981, recall: 0.0610, precision: 0.9900 },
  },
  COLLABORATIVE: {
    label: "Collaborative Filtering",
    tagline: "Personalized to the real interaction history of this user. It does not have the highest score, but its results are not random.",
    how: "This strategy uses classic item-item collaborative filtering. It calculates the adjusted-cosine similarity from real clickstream sessions. Then it increases the score of each candidate by its similarity to the products in the interaction history of this user. Products from the current session have more weight than products from the full history. The strategy also adds related products that the search did not find. If a user has no history, the strategy intentionally uses a named popularity mix and does not silently do nothing.",
    differs: "This is the only strategy that is personalized with the real interaction history of the user who sends the request. In the independent evaluation, its score is in the same narrow range as the other strategies. Here, personalization does not give a clear improvement on the query-level relevance grades of WANDS.",
    grounding: "Item-item collaborative filtering with adjusted-cosine normalization: Sarwar, Karypis, Konstan and Riedl, “Item-Based Collaborative Filtering Recommendation Algorithms,” WWW 2001. The cold-start fallback and the session recency weights follow two sources. These are Netflix's “Beyond the 5 stars” and the Pinterest articles about how it serves real-time user signals.",
    metrics: { ndcg: 0.9689, mrr: 0.9981, recall: 0.0609, precision: 0.9905 },
  },
  BANDIT: {
    label: "Bandit Exploration",
    tagline: "Its score is a little lower than the baseline. This is intentional. It is not a bug.",
    how: "This strategy uses Thompson Sampling with one arm for each category. Each category has a Beta(α,β) posterior. The start values of the posterior come from historical engagement rates in the clickstream. The posterior also uses the top category of the user who sends the request. A window limit sets how much one exploration draw can change the order of the list.",
    differs: "This is the only strategy that intentionally gives up some measured ranking quality. All other strategies try to put the best result first. This strategy uses some of that precision to show products with too little exposure. The expected benefit occurs over time, and one offline snapshot cannot measure it. Note: the priors come from historical data and are fixed when the system creates the strategy. There is no live reward loop yet, thus results do not go back into the arms.",
    grounding: "For Thompson Sampling and the explore/exploit tradeoff in ranked recommendations, see the Etsy engineering blog about OPAR (Optimizely-style Personalization). Also see the Spotify Research articles about contextual bandits for recommendation ranking. In offline evaluation, a score lower than the None baseline is expected. The real value of exploration is to show products with too little exposure over time. One held-out snapshot cannot show this value. See RESULTS.md.",
    metrics: { ndcg: 0.9447, mrr: 0.9945, recall: 0.0589, precision: 0.9939 },
  },
  NEURAL: {
    label: "Neural Ranking",
    tagline: "A learning-to-rank model that this project trained from zero on its own data.",
    how: "This strategy uses an XGBoost pairwise ranking model to give a score to each candidate. The script training/train_neural_ranker.py trains the model on the synthetic clickstream of this project with a rank:ndcg objective. The project then exports the model to ONNX. The model uses 7 features. They are category match, base search score, popularity, co-occurrence with the full user history, average rating, review count, and same-session category overlap. The last feature is different from the co-occurrence signal for the full history.",
    differs: "This is the only strategy that learned its ranking function from data. In the other strategies, a person wrote the ranking function. Note: a simple logistic regression baseline with the same features got a slightly higher score on the held-out split (0.8751 vs 0.8687 for XGBoost). Both scores increased by approximately 6-7 points when the project added the 7th feature. The project kept XGBoost because it can model feature interactions. But the difference is a real result and not rounding noise (see training/TRAINING.md).",
    grounding: "This strategy uses pairwise learning-to-rank (LambdaRank-style, with the XGBoost rank:ndcg objective). The features are cheap and are mostly in a cache, and there is one small inference pass. This follows the same “cheap features, no transformer” approach as the NeuralRerankStrategy/RerankFeatureBuilder pattern in the related `search` project.",
    metrics: { ndcg: 0.9615, mrr: 0.9975, recall: 0.0607, precision: 0.9966 },
  },
  DIVERSE_POPULARITY: {
    label: "Diverse Popularity",
    tagline: "Popularity, then an intentional spread across categories. Thus the top 5 are not 5 almost identical products.",
    how: "This strategy first runs Popularity. Then it does a greedy MMR (maximal marginal relevance) pass to change the order. For each next position, it selects the product with the highest relevance minus a penalty. The penalty increases when the product is similar to the products that are already above it. This strategy is a decorator and does not make its own ranking from zero. It can wrap the output of any strategy, but this demo uses it with Popularity.",
    differs: "This is the only strategy that intentionally gives up some ranking quality for more variety in the results. Bandit Exploration also makes an intentional tradeoff, but for a different goal. Bandit Exploration is for exploration, and this strategy is for category spread. In the two evaluation tables, its nDCG is lower than the nDCG of plain Popularity. This lower nDCG is the expected cost of the tradeoff. It is not a bug.",
    grounding: "See the Netflix articles “Recommendations: Beyond the 5 stars” Part 1 and Part 2. They show a problem when you optimize only one relevance score for each item, with no diversity term. The top-N list can then have high scores but contain redundant items. The similarity is the maximum of the real Pinecone embedding distance and a category/product-class proxy, not the proxy alone. The strategy does one embedding lookup for each candidate (see the DiversityAwareStrategy Javadoc in recommender-service).",
    metrics: { ndcg: 0.9061, mrr: 0.9981, recall: 0.0555, precision: 0.8916 },
  },
};

const statusEl = document.getElementById("status");
const columnsEl = document.getElementById("columns");
const selectEl = document.getElementById("query-select");
const glanceGridEl = document.getElementById("glance-grid");
const hoverCardEl = document.getElementById("hover-card");

let QUERY_DATA = {};

async function loadQueryData() {
  statusEl.textContent = "Loading captured query snapshots…";
  const manifestResponse = await fetch("data/manifest.json");
  if (!manifestResponse.ok) {
    statusEl.textContent = "No captured snapshots found under docs/data/ — run scripts/capture_demo_snapshots.py first.";
    return;
  }
  const files = await manifestResponse.json();
  const loaded = await Promise.all(files.map((name) => fetch(`data/${name}`).then((r) => r.json())));
  for (const entry of loaded) {
    QUERY_DATA[entry.query] = entry.resultsByStrategy;
  }
  statusEl.textContent = "";

  for (const query of Object.keys(QUERY_DATA)) {
    const option = document.createElement("option");
    option.value = query;
    option.textContent = query;
    selectEl.appendChild(option);
  }
  selectEl.addEventListener("change", () => renderColumns(selectEl.value));
  renderColumns(Object.keys(QUERY_DATA)[0]);
}

function formatRating(product) {
  if (!product.averageRating || !product.ratingCount) {
    return "No ratings yet";
  }
  return `★ ${product.averageRating.toFixed(1)} (${product.ratingCount})`;
}

function renderColumns(query) {
  columnsEl.innerHTML = "";
  const resultsByStrategy = QUERY_DATA[query];
  if (!resultsByStrategy) {
    return;
  }

  for (const strategyKey of STRATEGY_ORDER) {
    const data = resultsByStrategy[strategyKey];
    const info = STRATEGY_INFO[strategyKey];
    const column = document.createElement("div");
    column.className = "column";

    const header = document.createElement("div");
    header.className = "column-header";
    header.textContent = info.label;
    header.addEventListener("click", () => openModal(strategyKey));
    column.appendChild(header);

    if (data) {
      for (const product of data.products) {
        column.appendChild(renderProductCard(product));
      }
    }
    columnsEl.appendChild(column);
  }
}

function renderProductCard(product) {
  const card = document.createElement("div");
  card.className = "product-card";

  const name = document.createElement("div");
  name.className = "product-name";
  name.textContent = product.name;
  card.appendChild(name);

  const meta = document.createElement("div");
  meta.className = "product-meta";
  meta.innerHTML = `<span>${formatRating(product)}</span><span>${product.score.toFixed(3)}</span>`;
  card.appendChild(meta);

  const badge = document.createElement("div");
  badge.className = "source-badge";
  badge.textContent = humanizeSource(product.source);
  card.appendChild(badge);

  card.addEventListener("mouseenter", (event) => showHoverCard(product, event));
  card.addEventListener("mousemove", (event) => positionHoverCard(event));
  card.addEventListener("mouseleave", hideHoverCard);

  return card;
}

function showHoverCard(product, event) {
  document.getElementById("hover-name").textContent = product.name;
  document.getElementById("hover-category").textContent = product.categoryHierarchy || "—";
  document.getElementById("hover-class").textContent = product.productClass || "—";
  document.getElementById("hover-rating").textContent = formatRating(product);
  document.getElementById("hover-score").textContent = product.score.toFixed(4);
  document.getElementById("hover-source").textContent = humanizeSource(product.source);
  document.getElementById("hover-id").textContent = product.productId;
  hoverCardEl.hidden = false;
  positionHoverCard(event);
}

function positionHoverCard(event) {
  const offset = 16;
  const cardWidth = 260;
  const viewportWidth = window.innerWidth;
  let left = event.clientX + offset;
  if (left + cardWidth > viewportWidth) {
    left = event.clientX - cardWidth - offset;
  }
  hoverCardEl.style.left = `${left}px`;
  hoverCardEl.style.top = `${event.clientY + offset}px`;
}

function hideHoverCard() {
  hoverCardEl.hidden = true;
}

function renderGlanceGrid() {
  for (const strategyKey of STRATEGY_ORDER) {
    const info = STRATEGY_INFO[strategyKey];
    const card = document.createElement("div");
    card.className = "glance-card";
    card.innerHTML = `
      <h3>${info.label}</h3>
      <p>${info.tagline}</p>
      <span class="metric-pill">nDCG@5 ${info.metrics.ndcg.toFixed(4)}</span>
    `;
    card.addEventListener("click", () => openModal(strategyKey));
    glanceGridEl.appendChild(card);
  }
}

function openModal(strategyKey) {
  const info = STRATEGY_INFO[strategyKey];
  document.getElementById("modal-title").textContent = info.label;
  document.getElementById("modal-tagline").textContent = info.tagline;
  document.getElementById("modal-how").textContent = info.how;
  document.getElementById("modal-differs").textContent = info.differs;
  document.getElementById("modal-grounding").textContent = info.grounding;
  document.getElementById("modal-metrics-row").innerHTML = `
    <td>${info.metrics.ndcg.toFixed(4)}</td>
    <td>${info.metrics.mrr.toFixed(4)}</td>
    <td>${info.metrics.recall.toFixed(4)}</td>
    <td>${info.metrics.precision.toFixed(4)}</td>
  `;
  document.getElementById("modal-overlay").hidden = false;
}

document.getElementById("modal-close").addEventListener("click", () => {
  document.getElementById("modal-overlay").hidden = true;
});
document.getElementById("modal-overlay").addEventListener("click", (event) => {
  if (event.target.id === "modal-overlay") {
    event.currentTarget.hidden = true;
  }
});

renderGlanceGrid();
loadQueryData();
