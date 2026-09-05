export function Footer() {
  return (
    <footer className="border-t border-line bg-surface">
      <div className="mx-auto max-w-6xl px-4 py-6 text-center text-xs text-content-subtle">
        &copy; {new Date().getFullYear()} GROOVE. All rights reserved.
      </div>
    </footer>
  );
}
