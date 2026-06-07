export type UserRole = 'CUSTOMER' | 'ORGANIZER' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
}

export interface AuthSession {
  token: string;
  user: User;
}
