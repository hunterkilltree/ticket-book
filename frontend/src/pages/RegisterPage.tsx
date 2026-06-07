import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/store';
import { Input } from '@/components/Input';
import { Button } from '@/components/Button';

export function RegisterPage() {
  const navigate = useNavigate();
  const register = useAuthStore((s) => s.register);
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const ok = await register(fullName, email, password);
    setBusy(false);
    if (ok) navigate('/');
    else setError('Could not register. The email may already be in use.');
  }

  return (
    <section className="auth-form">
      <h1>Create account</h1>
      <form onSubmit={onSubmit}>
        <label>Full name<Input value={fullName} onChange={(e) => setFullName(e.target.value)} required /></label>
        <label>Email<Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
        <label>Password<Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} required /></label>
        {error && <p className="error-text">{error}</p>}
        <Button type="submit" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</Button>
      </form>
      <p className="muted">Already have an account? <Link to="/login">Log in</Link></p>
    </section>
  );
}
