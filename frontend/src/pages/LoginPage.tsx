import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/store';
import { Input } from '@/components/Input';
import { Button } from '@/components/Button';

export function LoginPage() {
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const ok = await login(email, password);
    setBusy(false);
    if (ok) navigate('/');
    else setError('Invalid email or password.');
  }

  return (
    <section className="auth-form">
      <h1>Log in</h1>
      <form onSubmit={onSubmit}>
        <label>Email<Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
        <label>Password<Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required /></label>
        {error && <p className="error-text">{error}</p>}
        <Button type="submit" disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</Button>
      </form>
      <p className="muted">No account? <Link to="/register">Register</Link></p>
    </section>
  );
}
