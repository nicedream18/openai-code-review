API_KEY='eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiIsInNpZ25fdHlwZSI6IlNJR04ifQ.eyJhcGlfa2V5IjoiMjAxZDA3M2RhM2UwNDMzNjlkMjY5ZjY3NzcyNmJjODYiLCJleHAiOjE3NzY2MDczNzUwODgsInRpbWVzdGFtcCI6MTc3NjYwNTU3NTEwMn0.okH77fNJg5TW3YnbBZvQDaKXg9uv2Nm2M2Y7V16d8Zc'

curl --location 'https://open.bigmodel.cn/api/paas/v4/chat/completions' \
  --header "Authorization: Bearer ${API_KEY}" \
  --header 'Content-Type: application/json' \
  --data-raw '{
    "model": "glm-4",
    "stream": true,
    "messages": [
      {
        "role": "user",
        "content": "1+1"
      }
    ]
  }'