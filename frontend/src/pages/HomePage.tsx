import { Link } from 'react-router-dom';

export function HomePage() {
  return (
    <section>
      <h1>Find your next concert</h1>
      <p>Browse upcoming shows, pick your seats live, and check out securely.</p>
      <p><Link to="/events">Browse events →</Link></p>
    </section>
  );
}
