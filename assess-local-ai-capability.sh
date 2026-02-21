#!/bin/bash
#
# Local AI Capability Assessment Script
# Evaluates hardware for running local LLM models (Ollama/Continue.dev)
#

echo "========================================"
echo "  LOCAL AI CAPABILITY ASSESSMENT"
echo "========================================"
echo ""

# Color codes for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Gather system information
echo "📊 SYSTEM INFORMATION"
echo "--------------------"

# OS Information
echo "OS: $(lsb_release -ds 2>/dev/null || cat /etc/*release 2>/dev/null | grep PRETTY_NAME | cut -d'"' -f2)"
echo "Kernel: $(uname -r)"
echo "Architecture: $(uname -m)"
echo ""

# CPU Information
echo "💻 CPU INFORMATION"
echo "------------------"
CPU_MODEL=$(lscpu | grep "Model name" | cut -d':' -f2 | xargs)
CPU_CORES=$(nproc --all)
CPU_THREADS=$(lscpu | grep "^CPU(s):" | awk '{print $2}')
echo "Model: $CPU_MODEL"
echo "Physical Cores: $CPU_CORES"
echo "Threads: $CPU_THREADS"
echo ""

# RAM Information
echo "🧠 MEMORY (RAM)"
echo "---------------"
TOTAL_RAM_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
TOTAL_RAM_GB=$(echo "scale=2; $TOTAL_RAM_KB/1024/1024" | bc)
AVAILABLE_RAM_KB=$(grep MemAvailable /proc/meminfo | awk '{print $2}')
AVAILABLE_RAM_GB=$(echo "scale=2; $AVAILABLE_RAM_KB/1024/1024" | bc)
echo "Total RAM: ${TOTAL_RAM_GB} GB"
echo "Available RAM: ${AVAILABLE_RAM_GB} GB"
echo ""

# GPU Information
echo "🎮 GPU INFORMATION"
echo "------------------"
if command -v nvidia-smi &> /dev/null; then
    GPU_NAME=$(nvidia-smi --query-gpu=name --format=csv,noheader | head -1)
    GPU_MEMORY=$(nvidia-smi --query-gpu=memory.total --format=csv,noheader | head -1)
    echo "NVIDIA GPU Detected: $GPU_NAME"
    echo "GPU Memory: $GPU_MEMORY"
    HAS_GPU=true
else
    echo "No NVIDIA GPU detected (will use CPU)"
    HAS_GPU=false
fi
echo ""

# Disk Space
echo "💾 DISK SPACE"
echo "-------------"
DISK_SPACE=$(df -h ~ | awk 'NR==2 {print $4}')
echo "Available in home directory: $DISK_SPACE"
echo ""

# Check for existing tools
echo "🔧 EXISTING TOOLS"
echo "-----------------"
echo -n "Docker: "
if command -v docker &> /dev/null; then
    echo -e "${GREEN}✓ Installed$(docker --version 2>/dev/null | cut -d' ' -f3 | tr -d ',')${NC}"
else
    echo -e "${YELLOW}✗ Not installed${NC}"
fi

echo -n "Python 3: "
if command -v python3 &> /dev/null; then
    echo -e "${GREEN}✓ Installed ($(python3 --version | cut -d' ' -f2))${NC}"
else
    echo -e "${RED}✗ Not installed${NC}"
fi

echo -n "Ollama: "
if command -v ollama &> /dev/null; then
    echo -e "${GREEN}✓ Installed ($(ollama --version 2>/dev/null))${NC}"
else
    echo -e "${YELLOW}✗ Not installed${NC}"
fi

echo -n "VS Code: "
if command -v code &> /dev/null; then
    echo -e "${GREEN}✓ Installed ($(code --version 2>/dev/null | head -1))${NC}"
else
    echo -e "${YELLOW}✗ Not installed${NC}"
fi
echo ""

# Model compatibility assessment
echo "========================================"
echo "  🎯 MODEL COMPATIBILITY ASSESSMENT"
echo "========================================"
echo ""

RAM_INT=$(echo "$TOTAL_RAM_GB" | cut -d'.' -f1)

echo "Based on your ${TOTAL_RAM_GB} GB RAM:"
echo ""

# CodeLlama 7B
if [ "$RAM_INT" -ge 8 ]; then
    echo -e "${GREEN}✓ CodeLlama 7B (4GB)${NC}"
    echo "  Quality: Fair | Speed: Fast"
    echo "  Use case: Simple refactoring, basic code generation"
    RECOMMENDED_7B=true
else
    echo -e "${RED}✗ CodeLlama 7B (4GB) - Insufficient RAM${NC}"
    RECOMMENDED_7B=false
fi
echo ""

# CodeLlama 13B
if [ "$RAM_INT" -ge 16 ]; then
    echo -e "${GREEN}✓ CodeLlama 13B (7GB)${NC}"
    echo "  Quality: Good | Speed: Medium"
    echo "  Use case: General coding tasks, moderate complexity"
    RECOMMENDED_13B=true
else
    echo -e "${RED}✗ CodeLlama 13B (7GB) - Insufficient RAM${NC}"
    RECOMMENDED_13B=false
fi
echo ""

# CodeLlama 34B
if [ "$RAM_INT" -ge 24 ]; then
    echo -e "${GREEN}✓ CodeLlama 34B (19GB) ⭐ RECOMMENDED${NC}"
    echo "  Quality: Very Good | Speed: Slow"
    echo "  Use case: Complex features, production code"
    RECOMMENDED_34B=true
else
    echo -e "${YELLOW}⚠ CodeLlama 34B (19GB) - May struggle${NC}"
    echo "  Your RAM: ${TOTAL_RAM_GB} GB (recommended: 24+ GB)"
    RECOMMENDED_34B=false
fi
echo ""

# CodeLlama 70B
if [ "$RAM_INT" -ge 48 ]; then
    echo -e "${GREEN}✓ CodeLlama 70B (39GB) ⭐⭐ PREMIUM${NC}"
    echo "  Quality: Excellent | Speed: Very Slow"
    echo "  Use case: Critical features, best quality"
    RECOMMENDED_70B=true
else
    echo -e "${RED}✗ CodeLlama 70B (39GB) - Insufficient RAM${NC}"
    RECOMMENDED_70B=false
fi
echo ""

# DeepSeek Coder
if [ "$RAM_INT" -ge 24 ]; then
    echo -e "${GREEN}✓ DeepSeek-Coder 33B (18GB)${NC}"
    echo "  Quality: Very Good | Speed: Slow"
    echo "  Use case: Alternative to CodeLlama 34B"
    RECOMMENDED_DEEPSEEK=true
else
    echo -e "${RED}✗ DeepSeek-Coder 33B (18GB) - Insufficient RAM${NC}"
    RECOMMENDED_DEEPSEEK=false
fi
echo ""

# Final Recommendation
echo "========================================"
echo "  📋 FINAL RECOMMENDATION"
echo "========================================"
echo ""

if [ "$RAM_INT" -lt 8 ]; then
    echo -e "${RED}❌ LOCAL AI NOT RECOMMENDED${NC}"
    echo ""
    echo "Your system has insufficient RAM for running local LLMs."
    echo ""
    echo "RECOMMENDED ALTERNATIVES:"
    echo "1. ⭐ Gemini 2.0 Flash (FREE, cloud-based)"
    echo "   • Zero cost forever"
    echo "   • High quality"
    echo "   • 15-20 days for 38 tasks"
    echo ""
    echo "2. DeepSeek API ($0.10 total)"
    echo "   • Ultra-cheap"
    echo "   • 1-2 days for 38 tasks"
    
elif [ "$RAM_INT" -ge 24 ]; then
    echo -e "${GREEN}✅ EXCELLENT FOR LOCAL AI${NC}"
    echo ""
    echo "Your system can run high-quality local models!"
    echo ""
    echo "RECOMMENDED SETUP:"
    echo "1. Install Ollama: curl -fsSL https://ollama.com/install.sh | sh"
    echo "2. Pull CodeLlama 34B: ollama pull codellama:34b-instruct"
    echo "3. Install Continue.dev: code --install-extension continue.continue"
    echo ""
    echo "EXPECTED PERFORMANCE:"
    echo "• Inference speed: 2-5 tokens/sec (CPU)"
    if [ "$HAS_GPU" = true ]; then
        echo "• With GPU: 10-20 tokens/sec (much faster!)"
    fi
    echo "• Task completion: 5-15 minutes per task"
    echo "• Cost: $0 forever"
    echo "• Privacy: Complete (data never leaves your machine)"
    
elif [ "$RAM_INT" -ge 16 ]; then
    echo -e "${YELLOW}⚠️ ACCEPTABLE FOR LOCAL AI${NC}"
    echo ""
    echo "Your system can run medium-quality local models."
    echo ""
    echo "RECOMMENDED SETUP:"
    echo "1. Install Ollama: curl -fsSL https://ollama.com/install.sh | sh"
    echo "2. Pull CodeLlama 13B: ollama pull codellama:13b-instruct"
    echo "3. Install Continue.dev: code --install-extension continue.continue"
    echo ""
    echo "ALTERNATIVE (BETTER QUALITY):"
    echo "• Gemini 2.0 Flash (FREE, cloud-based)"
    echo "  Higher quality than 13B model, but rate limited"
    
else
    echo -e "${YELLOW}⚠️ MARGINAL FOR LOCAL AI${NC}"
    echo ""
    echo "Your system can run small models with limited capabilities."
    echo ""
    echo "RECOMMENDED: Use cloud alternatives instead"
    echo "• Gemini 2.0 Flash (FREE, better quality)"
    echo "• DeepSeek API ($0.10 total, high quality)"
fi

echo ""
echo "========================================"
echo "  💡 ESTIMATED COSTS COMPARISON"
echo "========================================"
echo ""
echo "For completing 38 tasks:"
echo ""
printf "%-25s %10s %15s %15s\n" "Solution" "Cost" "Timeline" "Your System"
echo "----------------------------------------------------------------"

if [ "$RAM_INT" -ge 24 ]; then
    printf "%-25s %10s %15s %15s\n" "Local Ollama 34B" "\$0" "2-4 weeks" "✓ Compatible"
elif [ "$RAM_INT" -ge 16 ]; then
    printf "%-25s %10s %15s %15s\n" "Local Ollama 13B" "\$0" "3-5 weeks" "✓ Compatible"
else
    printf "%-25s %10s %15s %15s\n" "Local Ollama" "\$0" "N/A" "✗ Insufficient RAM"
fi

printf "%-25s %10s %15s %15s\n" "Gemini 2.0 Flash" "\$0" "15-20 days" "✓ Recommended"
printf "%-25s %10s %15s %15s\n" "DeepSeek API" "\$0.10" "1-2 days" "✓ Best value"
printf "%-25s %10s %15s %15s\n" "Claude Sonnet" "\$5.81" "1-2 days" "✓ Premium"

echo ""
echo "========================================"
echo "Assessment complete!"
echo "========================================"
