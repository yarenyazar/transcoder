import requests

# 1. Login to get token
login_data = {
    "username": "admin",
    "password": "password"
}
# Try to login, if it fails because user doesn't exist, we will try to register
try:
    r = requests.post("http://localhost:8082/api/auth/login", json=login_data)
    if r.status_code == 403 or r.status_code == 401 or r.status_code == 404:
        print("Login failed, trying to register...")
        r = requests.post("http://localhost:8082/api/auth/register", json=login_data)
        print("Register status:", r.status_code)
        r = requests.post("http://localhost:8082/api/auth/login", json=login_data)
    
    print("Login status:", r.status_code)
    token = r.json().get("token")
    if token:
        print("Token received:", token[:10] + "...")
        # 2. Call /api/transcode/files
        headers = {"Authorization": f"Bearer {token}"}
        res = requests.get("http://localhost:8082/api/transcode/files", headers=headers)
        print("Files status:", res.status_code)
        print("Files payload:", res.text)
    else:
        print("No token in response", r.text)

except Exception as e:
    print("Error:", str(e))
