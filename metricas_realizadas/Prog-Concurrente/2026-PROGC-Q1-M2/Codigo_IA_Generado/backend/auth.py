from datetime import datetime, timedelta, timezone
from typing import Optional

import jwt
from passlib.context import CryptContext
from fastapi import HTTPException, status, Depends
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from database import get_cursor
from config import SECRET_KEY, ALGORITHM, ACCESS_TOKEN_EXPIRE_MINUTES

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
security = HTTPBearer()


def hash_password(password: str) -> str:
    return pwd_context.hash(password)


def verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain, hashed)


def create_access_token(data: dict, expires_delta: Optional[timedelta] = None) -> str:
    to_encode = data.copy()
    if "sub" in to_encode:
        to_encode["sub"] = str(to_encode["sub"])
    expire = datetime.now(timezone.utc) + (
        expires_delta or timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    )
    to_encode["exp"] = expire
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)


def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
) -> dict:
    """
    Valida el JWT y devuelve {id, username} sin tocar la base de datos.
    Los datos de usuario completos (email, full_name) se obtienen solo cuando
    el endpoint los necesita explícitamente (ej: GET /api/auth/me).
    """
    token = credentials.credentials
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Credenciales inválidas",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        user_id = payload.get("sub")
        username = payload.get("username")
        if user_id is None or username is None:
            raise credentials_exception
        user_id = int(user_id)
    except jwt.PyJWTError:
        raise credentials_exception
    except (TypeError, ValueError):
        raise credentials_exception

    return {"id": user_id, "username": username}


def authenticate_user(username: str, password: str) -> Optional[dict]:
    with get_cursor(commit=False) as (cur, conn):
        cur.execute(
            "SELECT id, username, password_hash, email, full_name FROM users WHERE username = %s",
            (username,),
        )
        user = cur.fetchone()

    if user is None or not verify_password(password, user["password_hash"]):
        return None
    return dict(user)
