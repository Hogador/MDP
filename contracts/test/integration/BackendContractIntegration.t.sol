// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test, console} from "forge-std/Test.sol";
import {MDAOPaymaster, UserOperation, IEntryPointView} from "../../src/MDAOPaymaster.sol";
import {MDAOToken} from "../../src/MDAOToken.sol";

/// @dev Minimal EntryPoint stub — passes extcodesize check, exposes balanceOf.
contract MockEntryPoint is IEntryPointView {
    mapping(address => uint256) public override balanceOf;
    receive() external payable {}
    fallback(bytes calldata) external returns (bytes memory) { return ""; }
    /// @notice Helper to set paymaster deposit balance.
    function setBalance(address account, uint256 amount) external { balanceOf[account] = amount; }
}

/// @dev Mock USDT (6 decimals, non-permit) — required by MDAOPaymaster constructor.
contract MockUSDT {
    string public name = "USDT";
    string public symbol = "USDT";
    uint8 public constant decimals = 6;
    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;

    function mint(address to, uint256 amount) external { balanceOf[to] += amount; }
    function approve(address spender, uint256 amount) external returns (bool) {
        allowance[msg.sender][spender] = amount;
        return true;
    }
    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        allowance[from][msg.sender] -= amount;
        balanceOf[from] -= amount;
        balanceOf[to] += amount;
        return true;
    }
}

contract BackendContractIntegrationTest is Test {
    MockEntryPoint ep;
    MDAOToken token;
    MockUSDT usdt;
    MDAOPaymaster paymaster;

    uint256 constant SIGNER_KEY = 0xA11CE;
    address trustedSigner;

    function setUp() public {
        trustedSigner = vm.addr(SIGNER_KEY);

        ep = new MockEntryPoint();
        // Real MDAOToken — test contract is owner, can mint later
        token = new MDAOToken(address(this));
        usdt = new MockUSDT();

        paymaster = new MDAOPaymaster(
            address(ep),
            address(token),
            address(usdt),
            trustedSigner
        );

        // Set token price for non-zero price check in _validateFields
        paymaster.setTokenPrice(address(token), 2000 ether);
    }

    /// @dev Compute EIP-712 digest matching MDAOPaymaster._verifyQuoteSignature.
    function _quoteDigest(
        address sender,
        address tokenAddr,
        uint256 maxAmt,
        uint256 maxGas,
        uint256 deadline,
        uint256 nonce
    ) internal view returns (bytes32) {
        bytes32 structHash = keccak256(abi.encode(
            paymaster.QUOTE_TYPEHASH(),
            sender,
            tokenAddr,
            maxAmt,
            maxGas,
            deadline,
            nonce
        ));
        // Match OZ _hashTypedDataV4: keccak256(abi.encodePacked("\x19\x01", domainSeparator, structHash))
        return keccak256(abi.encodePacked("\x19\x01", paymaster.DOMAIN_SEPARATOR(), structHash));
    }

    /// @notice F-105: Backend EIP-712 signature verifies on-chain end-to-end.
    ///         Uses real MDAOToken (not a mock), fixture-based, no forge ffi.
    function test_BackendSignatureVerifiesOnChain() public {
        address user = address(0x1234);

        // ── Setup: mint real MDAOToken to user, approve paymaster ──
        token.mint(user, 100 ether);
        vm.prank(user);
        token.approve(address(paymaster), 10 ether);

        uint256 maxAmt = 1 ether;
        uint256 maxGas = 10 gwei;
        uint256 deadline = block.timestamp + 600;

        // ── Build UserOperation ──
        UserOperation memory userOp = UserOperation({
            sender: user,
            nonce: 0,
            initCode: "",
            callData: "",
            callGasLimit: 0,
            verificationGasLimit: 200_000,
            preVerificationGas: 0,
            maxFeePerGas: maxGas,
            maxPriorityFeePerGas: 1 gwei,
            paymasterAndData: "",
            signature: ""
        });

        // ── Build paymasterData fields: token(20) + maxAmt(32) + deadline(32) = 84 bytes ──
        bytes memory paymasterDataRaw = abi.encodePacked(address(token), maxAmt, deadline);

        // ── EIP-712 quote (nonce = 0 for first call) ──
        bytes32 digest = _quoteDigest(user, address(token), maxAmt, maxGas, deadline, 0);

        // ── Simulate backend signing ──
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(SIGNER_KEY, digest);
        bytes memory sig = abi.encodePacked(r, s, v);

        // ── Append suffix: sig(65) + lenHex(2) + magic(8) = 75 bytes ──
        bytes8 magic = bytes8(hex"22e325a297439656");
        bytes2 lenHex = bytes2(uint16(sig.length));
        bytes memory paymasterData = abi.encodePacked(paymasterDataRaw, sig, lenHex, magic);

        // ── Fund the paymaster's entry point deposit ──
        ep.setBalance(address(paymaster), 1 ether);

        // ── Call as EntryPoint ──
        vm.prank(address(ep));
        (bytes memory ctx, uint256 validationData) = paymaster.validatePaymasterUserOp(
            userOp, bytes32(0), 0.1 ether, paymasterData
        );

        // ── Assertions ──
        assertEq(validationData, 0, "Backend signature must verify on-chain");
        assertGt(ctx.length, 0, "Context must be non-empty");

        // Nonce must be consumed (anti-replay)
        assertEq(paymaster.nextQuoteNonce(user), 1, "Nonce must increment after verification");

        console.log("BackendContractIntegration: EIP-712 signature verified on-chain");
    }
}
