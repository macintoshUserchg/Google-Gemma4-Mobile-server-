
#!/bin/bash
BASE_URL="http://192.168.1.16:8080"
API_KEY="mobile@123"

# Colors
GREEN="\03[0;32m"
RED="\03[0;31m"
NC="\03[0m"

echo "Running robust security and compatibility tests..."

# 1. Health check (should not require API key)
echo -n "Test 1: Health check without API key... "
status=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/health)
if [ "$status" = "200" ]; then echo -e "${GREEN}PASS${NC}"; else echo -e "${RED}FAIL (Status: $status)${NC}"; fi

# 2. Get Models without API key (Should fail 401)
echo -n "Test 2: GET /v1/models WITHOUT API key... "
status=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/v1/models)
if [ "$status" = "401" ]; then echo -e "${GREEN}PASS${NC}"; else echo -e "${RED}FAIL (Status: $status)${NC}"; fi

# 3. Get Models with WRONG API key (Should fail 401)
echo -n "Test 3: GET /v1/models with WRONG API key... "
status=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer wrong_key" $BASE_URL/v1/models)
if [ "$status" = "401" ]; then echo -e "${GREEN}PASS${NC}"; else echo -e "${RED}FAIL (Status: $status)${NC}"; fi

# 4. Get Models with CORRECT API key (Should pass 200)
echo -n "Test 4: GET /v1/models with CORRECT API key... "
status=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $API_KEY" $BASE_URL/v1/models)
if [ "$status" = "200" ]; then echo -e "${GREEN}PASS${NC}"; else echo -e "${RED}FAIL (Status: $status)${NC}"; fi

# 5. Chat Completions without API key (Should fail 401)
echo -n "Test 5: POST /v1/chat/completions WITHOUT API key... "
status=$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" -d "{\"model\":\"Gemma\",\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}" $BASE_URL/v1/chat/completions)
if [ "$status" = "401" ]; then echo -e "${GREEN}PASS${NC}"; else echo -e "${RED}FAIL (Status: $status)${NC}"; fi

# 6. Chat Completions with WRONG API key (Should fail 401)
echo -n "Test 6: POST /v1/chat/completions with WRONG API key... "
status=$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "Authorization: Bearer bad_key" -H "Content-Type: application/json" -d "{\"model\":\"Gemma\",\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}" $BASE_URL/v1/chat/completions)
if [ "$status" = "401" ]; then echo -e "${GREEN}PASS${NC}"; else echo -e "${RED}FAIL (Status: $status)${NC}"; fi

# 7. Chat Completions with CORRECT API key (Should pass 200)
echo -n "Test 7: POST /v1/chat/completions with CORRECT API key... "
response=$(curl -s -w "%{http_code}" -X POST -H "Authorization: Bearer $API_KEY" -H "Content-Type: application/json" -d "{\"model\":\"Gemma\",\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}" $BASE_URL/v1/chat/completions)
status="${response: -3}"
if [ "$status" = "200" ]; then echo -e "${GREEN}PASS${NC}"; else echo -e "${RED}FAIL (Status: $status)${NC}"; fi

