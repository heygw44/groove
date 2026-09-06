export function Footer() {
  return (
    <footer className="border-t border-line bg-surface">
      <div className="mx-auto max-w-6xl px-4 py-6 text-center text-xs text-content-subtle">
        <p>
          This application uses Discogs&#39; API but is not affiliated with, sponsored or endorsed
          by Discogs. &#39;Discogs&#39; is a trademark of Zink Media, LLC.
        </p>
        <p className="mt-1">&copy; {new Date().getFullYear()} GROOVE. All rights reserved.</p>
      </div>
    </footer>
  );
}
