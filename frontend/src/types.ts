// Espelham os records do backend (pacote com.bicavi.auth).

export interface UserResponse {
  id: number
  email: string
  name: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}
