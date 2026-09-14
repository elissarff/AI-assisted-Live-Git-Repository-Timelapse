#!/bin/bash

echo "======================================"
echo " GitHub Webhook Test"
echo "======================================"
echo

# Your repository
REPO="elissarff/AI-assisted-Live-Git-Repository-Timelapse"
REPO_ID="1362628666"

echo "1. Checking Spring Boot..."
if curl -s --fail http://localhost:8080/health > /dev/null; then
    echo "✓ Backend is running on port 8080"
else
    echo "✗ Backend is NOT running."
    echo "Start it with: ./mvnw spring-boot:run"
    exit 1
fi

echo
echo "2. Checking ngrok..."

if ! command -v ngrok > /dev/null; then
    echo "✗ ngrok is not installed."
    exit 1
fi

echo "✓ ngrok is installed"

echo
echo "3. Starting ngrok..."
echo
echo "When ngrok starts, copy the HTTPS Forwarding URL."
echo
echo "Your GitHub webhook Payload URL will be:"
echo
echo "https://abc123.ngrok-free.app/api/webhooks/github"
echo
echo "Keep this terminal OPEN while testing."
echo
echo "======================================"
echo " GitHub Configuration"
echo "======================================"
echo
echo "Repository:"
echo "  https://github.com/$REPO"
echo
echo "Go to:"
echo "  Settings -> Webhooks -> Add webhook"
echo
echo "Configure:"
echo "  Content type: application/json"
echo "  Event: Just the push event"
echo "  Secret: MUST match GITHUB_WEBHOOK_SECRET"
echo
echo "Repository GitHub ID:"
echo "  $REPO_ID"
echo
echo "After configuration:"
echo "  1. Push a new commit"
echo "  2. Watch the Spring Boot logs"
echo "  3. Check GitHub -> Settings -> Webhooks -> Recent Deliveries"
echo
echo "Starting ngrok now..."
echo

ngrok http 8080