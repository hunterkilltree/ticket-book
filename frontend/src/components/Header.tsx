import { Link } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/store';
import { Button } from './Button';

export function Header() {
  const { isAuthenticated, user, logout } = useAuthStore();
  const isStaff = user?.role === 'ADMIN' || user?.role === 'ORGANIZER';
  return (
    <header className="site-header">
      <Link to="/"><strong>🎟 Concert Tickets</strong></Link>
      <nav>
        <Link to="/events">Browse</Link>
        <Link to="/tickets">My Tickets</Link>
        {isStaff && <Link to="/admin">Admin</Link>}
        {isAuthenticated ? (
          <>
            {user && <span className="muted">{user.fullName}</span>}
            <Button variant="ghost" onClick={logout}>Log out</Button>
          </>
        ) : (
          <Link to="/login">Log in</Link>
        )}
      </nav>
    </header>
  );
}
