#pragma once
#include <windows.h>
#include <functional>
#include "kelpie/peer_approval.h"

namespace kelpie::windows {
void PollComputerApproval(PeerApprovalQueue& approvals, HWND owner,
                          const std::function<bool()>& owner_current);
void ShowTrustedComputers(HWND owner);
}
