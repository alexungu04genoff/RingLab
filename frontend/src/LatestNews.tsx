import { useLoad } from "./useLoad";
import type { GameNewsItem } from "./types";

export function LatestNews({ onHide }: { onHide?: () => void }) {
  const news = useLoad<GameNewsItem[]>("/news");
  return (
    <aside className="latest-news panel" aria-labelledby="latest-news-heading">
      <div className="latest-news-heading">
        <div>
          <h2 id="latest-news-heading">Latest news</h2>
          <p className="muted">From Steam</p>
        </div>
        {onHide && <button type="button" className="news-toggle" onClick={onHide}>Hide news</button>}
      </div>
      {news.loading ? (
        <p role="status">Loading news…</p>
      ) : news.error ? (
        <p role="status">News unavailable</p>
      ) : !news.data?.length ? (
        <p>No news yet.</p>
      ) : (
        <ul>
          {news.data.map((item) => (
            <li key={item.id}>
              <a href={item.url} target="_blank" rel="noreferrer">
                {item.title}
              </a>
              <time dateTime={item.publishedAt}>
                {new Date(item.publishedAt).toLocaleDateString(undefined, {
                  year: "numeric", month: "short", day: "numeric",
                })}
              </time>
            </li>
          ))}
        </ul>
      )}
    </aside>
  );
}
