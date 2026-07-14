import requests
import time
import json
import redis
import sys
import os

files = [
    ('files', open('d:/Capstone/source/mkaix.healthsyncbe/9000622_20050613_00831903.dcm', 'rb')),
    ('files', open('d:/Capstone/source/mkaix.healthsyncbe/9000099_20050531_00839603.dcm', 'rb'))
]

print("Uploading files...")
response = requests.post('http://localhost:8080/api/v1/dicom/upload/batch', files=files)
print("Upload response:", response.status_code, response.text)

print("Waiting 3 seconds for background processing...")
time.sleep(3)

r = redis.Redis(host='localhost', port=6379, decode_responses=True)
keys = r.keys('uploadSession:*')

if not keys:
    print("No upload session found in Redis!")
    sys.exit(1)

session_key = keys[0]
session_id = session_key.split(':')[1]
print(f"Found session ID: {session_id}")

session_data = json.loads(r.get(session_key))
patients = list(session_data.get('patients', {}).keys())
print(f"Patients found in session: {patients}")

if len(patients) < 2:
    print("Expected 2 patients in session, found:", len(patients))
    sys.exit(1)

# We will accept the first patient, reject the second
accepted_patient = patients[0]
rejected_patient = patients[1]

# Save paths of rejected patient to check deletion
rejected_paths = list(session_data['patients'][rejected_patient]['physicalFilePaths'].values())
print("Paths of rejected patient to check:", rejected_paths)

print(f"\nVerifying session with only accepted patient: {accepted_patient}")
verify_payload = {
    "uploadSessionId": session_id,
    "acceptedPatientCodes": [accepted_patient]
}

verify_response = requests.post(
    'http://localhost:8080/api/v1/dicom/verify',
    json=verify_payload,
    headers={'Content-Type': 'application/json'}
)
print("Verify response:", verify_response.status_code, verify_response.text)

print("Checking if Redis keys were deleted...")
if r.exists(session_key):
    print("ERROR: Session key still exists in Redis!")
else:
    print("SUCCESS: Session key deleted from Redis.")

print("Checking if rejected files were deleted from disk...")
for path in rejected_paths:
    if os.path.exists(path):
        print(f"ERROR: File {path} still exists!")
    else:
        print(f"SUCCESS: File {path} was correctly deleted.")
