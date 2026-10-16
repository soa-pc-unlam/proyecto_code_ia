#! /bin/bash

# Parse options
options=$(getopt -o "h" --long "help" -- "$@")

if [ $? -ne 0 ]; then
	echo -e "\e[31mAn error occurred on parsing options.\e[0m" >&2
	exit 1
fi

eval set -- "$options"

while true; do
	case "$1" in
		"-h" | "--help")
			need_help="true"
			shift 1
			break
			;;
		"--")
			shift
			break
			;;
		*)
			echo -e "\e[31mAn internal error occurred!\e[0m" >&2
			exit 1
			;;
	esac
done

# Exit on any command error
set -e

# Show help if needed
if [ -n "$need_help" ]; then
	printf "Usage: $0 [OPTION]...

Setup project tools.

Options:
	-h, --help   display this help and exit
"
	exit 0
fi

# Change from script directory to project root directory
cd $(cd "$(dirname "$0")/.." && pwd)

# Install packages (formatters, and git hooks manager)
echo -e "\e[90m\nInstalling project tools (formatters, and git hooks manager)...\n\e[0m"

echo "allowBuilds:
  lefthook: true
" > pnpm-workspace.yaml

pnpm install -D \
	oxfmt@^0 \
	lefthook@^2

echo -e "\e[32m\nProject tools installed successfully.\e[0m"

# Pull images
zizmor_image="ghcr.io/zizmorcore/zizmor:1.22.0" # Linter for GitHub Actions workflows.

echo -e "\e[90m\nPulling docker images project tools...\n\e[0m"

docker pull "$zizmor_image"

echo -e "\e[32m\nDocker images pulled successfully.\e[0m"

# Create bin scripts
echo "#! /bin/bash

docker run \\
	--rm \\
	--name zizmor \\
	--volume /workspaces/SOA-Practical-Work-2026/:/workspaces/SOA-Practical-Work-2026/ \\
	--workdir /workspaces/SOA-Practical-Work-2026/ \\
	$zizmor_image \\
	\"\$@\"
" > /usr/local/bin/zizmor

# Allow execution of scripts
chmod +x /usr/local/bin/zizmor
chmod +x /workspaces/SOA-Practical-Work-2026/scripts/*.sh

# Set git configuration and hooks
echo -e "\e[90m\nSetting up git configuration and hooks...\e[0m"

git config --global --add safe.directory /workspaces/SOA-Practical-Work-2026

pnpm exec lefthook install

echo -e "\e[32m\nGit configuration and hooks set successfully.\e[0m"

# Health check
echo -e "\e[90m\nRunning health check...\n\e[0m"

bash scripts/health-check.sh

echo -e "\e[32m\nAll tools are installed and working correctly.\e[0m"

# Final step
echo -e "\e[33m\nAs a final step, you have to press \`F1\` and select \"Wokwi: Request a new License\" option.\e[0m"
