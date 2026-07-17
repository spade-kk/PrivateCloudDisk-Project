import time, json, base64
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.backends import default_backend

with open('../PrivateCloudDisk-platform-service/src/main/resources/keys/private_key.pem', 'rb') as f:
    private_key = serialization.load_pem_private_key(f.read(), password=None, backend=default_backend())

header = {"alg": "RS256", "typ": "JWT"}
now = int(time.time())
payload = {"sub": "55555555-5555-5555-5555-555555555555", "iat": now, "exp": now + 30 * 24 * 3600}

def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode()

header_b64 = b64url(json.dumps(header, separators=(',', ':')).encode())
payload_b64 = b64url(json.dumps(payload, separators=(',', ':')).encode())
signing_input = f'{header_b64}.{payload_b64}'.encode()
signature = private_key.sign(signing_input, padding.PKCS1v15(), hashes.SHA256())
signature_b64 = b64url(signature)

token = f'{header_b64}.{payload_b64}.{signature_b64}'
print(token)
