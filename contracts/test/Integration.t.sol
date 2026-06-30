// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test, console} from "forge-std/Test.sol";
import {MDAOToken} from "../src/MDAOToken.sol";
import {MDAOPaymaster, UserOperation, IEntryPointView} from "../src/MDAOPaymaster.sol";
import {SocialRecoveryModule} from "../src/SocialRecoveryModule.sol";
import {NicknameRegistry} from "../src/NicknameRegistry.sol";
import {SessionKeyModule} from "../src/SessionKeyModule.sol";
import {MockP256} from "./mocks/MockP256.sol";

/// @dev Minimal EntryPoint stub — passes extcodesize check, accepts ETH, exposes balanceOf.
contract MockEntryPoint is IEntryPointView {
    mapping(address => uint256) public override balanceOf;
    receive() external payable {}
    fallback(bytes calldata) external returns (bytes memory) { return "";
    }
}

/// @dev Mock ERC-20 with permit (for MDAOPaymaster tests).
contract MockERC20 {
    string public name = "MDAO";
    string public symbol = "MDAO";
    uint8 public constant decimals = 18;
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

contract IntegrationTest is Test {
    MockEntryPoint ep;
    MockP256 mockP256;

    // C-1: EIP-712 quote digest helper
    function _quoteDigest(
        MDAOPaymaster pm,
        address sender,
        address token,
        uint256 maxAmt,
        uint256 maxGas,
        uint256 deadline,
        uint256 nonce
    ) internal view returns (bytes32) {
        bytes32 structHash = keccak256(abi.encode(
            pm.QUOTE_TYPEHASH(), sender, token, maxAmt, maxGas, deadline, nonce
        ));
        // Return raw EIP-712 digest — vm.sign adds EIP-191 prefix automatically
        return keccak256(abi.encodePacked("\x19\x01", pm.DOMAIN_SEPARATOR(), structHash));
    }

    // ═══════════════════════════════════════════════
    //  test_DeployAndCall — deploy all 5, call functions
    // ═══════════════════════════════════════════════

    function test_DeployAndCall() public {
        address deployer = makeAddr("deployer");
        ep = new MockEntryPoint();
        MockERC20 usdtMock = new MockERC20();

        vm.startPrank(deployer);

        MDAOToken token = new MDAOToken(deployer);
        NicknameRegistry nickReg = new NicknameRegistry();
        MockP256 mockP256 = new MockP256();
        SocialRecoveryModule recovery = new SocialRecoveryModule(address(token), address(mockP256));
        SessionKeyModule sessionKeys = new SessionKeyModule();
        MDAOPaymaster paymaster = new MDAOPaymaster(address(ep), address(token), address(usdtMock), address(0));

        vm.stopPrank();

        // Token
        assertEq(token.name(), "MDAO");
        assertEq(token.totalSupply(), 100_000_000 ether);

        // NicknameRegistry — resolve should revert for unknown
        vm.expectRevert(abi.encodeWithSelector(NicknameRegistry.NicknameNotRegistered.selector, keccak256("nobody")));
        nickReg.resolve(keccak256("nobody"));

        // SocialRecoveryModule — register wallet
        vm.prank(makeAddr("walletOwner"));
        recovery.registerWallet(bytes32(uint256(0x1)), bytes32(uint256(0x2)));

        // Paymaster — check entryPoint stored
        assertEq(paymaster.entryPoint(), address(ep));

        // SessionKeyModule — create + validate
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = keccak256("payments.send");
        vm.prank(deployer);
        bytes32 keyId = sessionKeys.createSessionKey(makeAddr("dapp"), block.timestamp + 1 days, perms, 1 ether, 0);
        sessionKeys.validateSessionKey(keyId, keccak256("payments.send"), 0.5 ether);

        console.log("Integration: DeployAndCall -- all 5 contracts deployed and callable");
    }

    // ═══════════════════════════════════════════════
    //  test_PaymasterFlow — deploy → set signer → build op → validate
    // ═══════════════════════════════════════════════

    function test_PaymasterFlow() public {
        address owner = makeAddr("owner");
        address user = makeAddr("user");
        uint256 signerKey = 0xA11CE;
        address signer = vm.addr(signerKey);

        ep = new MockEntryPoint();
        MockERC20 mdaoMock = new MockERC20();
        MockERC20 usdtMock = new MockERC20();

        vm.prank(owner);
        MDAOPaymaster paymaster = new MDAOPaymaster(address(ep), address(mdaoMock), address(usdtMock), signer);
        assertEq(paymaster.trustedSigner(), signer);

        vm.prank(owner);
        paymaster.setTokenPrice(address(mdaoMock), 2000 ether);

        mdaoMock.mint(user, 100 ether);
        vm.prank(user);
        mdaoMock.approve(address(paymaster), 10 ether);

        uint256 maxAmt = 1 ether;
        uint256 maxGas = 10 gwei;
        uint256 deadline = block.timestamp + 600;

        UserOperation memory userOp = UserOperation({
            sender: user, nonce: 0, initCode: "", callData: "",
            callGasLimit: 0, verificationGasLimit: 200_000,
            preVerificationGas: 0, maxFeePerGas: maxGas,
            maxPriorityFeePerGas: 1 gwei, paymasterAndData: "", signature: ""
        });

        bytes memory paymasterDataRaw = abi.encodePacked(address(mdaoMock), maxAmt, deadline);

        // C-1+C-8: EIP-712 signature in paymasterData suffix
        bytes32 digest = _quoteDigest(paymaster, user, address(mdaoMock), maxAmt, maxGas, deadline, 0);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(signerKey, digest);
        bytes memory sig = abi.encodePacked(r, s, v);
        bytes8 magic = hex"22e325a297439656";
        bytes2 lenHex = bytes2(uint16(sig.length));
        bytes memory paymasterData = abi.encodePacked(paymasterDataRaw, sig, lenHex, magic);

        vm.mockCall(address(ep), abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)), abi.encode(uint256(1 ether)));

        vm.prank(address(ep));
        (bytes memory ctx, uint256 valData) = paymaster.validatePaymasterUserOp(userOp, bytes32(0), 0.1 ether, paymasterData);
        assertEq(valData, 0);
        assertGt(ctx.length, 0);

        console.log("Integration: PaymasterFlow -- validatePaymasterUserOp passed");
    }

    // ═══════════════════════════════════════════════
    //  test_RecoveryFlow — initiate → approve → execute
    // ═══════════════════════════════════════════════

    function test_RecoveryFlow() public {
        MDAOToken token = new MDAOToken(address(this));
        MockP256 mockP256Local = new MockP256();
        SocialRecoveryModule recovery = new SocialRecoveryModule(address(token), address(mockP256Local));

        (address alice, bytes32[3] memory ids, address[3] memory guardians) = _setupGuardians(recovery);

        bytes memory newPubKey = hex"00000000000000000000000000000000000000000000000000000000000000050000000000000000000000000000000000000000000000000000000000000006";

        // Alice gets MDAO tokens and approves the recovery contract
        token.mint(alice, 100 ether);
        vm.prank(alice);
        token.approve(address(recovery), type(uint256).max);

        vm.prank(alice);
        recovery.initiateRecovery(alice, newPubKey);

        _mockApprovals(recovery, alice, newPubKey);
        // Only 2 approvals needed for 2-of-3 threshold
        for (uint256 i = 0; i < 2; i++) {
            _approveOne(recovery, alice, ids[i], guardians[i]);
        }

        vm.warp(block.timestamp + 48 hours + 1);
        vm.prank(alice);
        recovery.executeRecovery(alice);

        assertEq(recovery.ownerPasskeyHash(alice), keccak256(newPubKey));
        console.log("Integration: RecoveryFlow -- complete");
    }

    function _setupGuardians(SocialRecoveryModule recovery) internal returns (address alice, bytes32[3] memory ids, address[3] memory guardians) {
        alice = makeAddr("alice");
        guardians[0] = makeAddr("guardianA");
        guardians[1] = makeAddr("guardianB");
        guardians[2] = makeAddr("guardianC");

        vm.prank(alice);
        recovery.registerWallet(bytes32(uint256(0xa1)), bytes32(uint256(0xa2)));

        bytes32[3] memory xs = [bytes32(uint256(1)), bytes32(uint256(3)), bytes32(uint256(5))];
        bytes32[3] memory ys = [bytes32(uint256(2)), bytes32(uint256(4)), bytes32(uint256(6))];

        for (uint256 i = 0; i < 3; i++) {
            ids[i] = keccak256(abi.encodePacked(guardians[i]));
            vm.prank(alice);
            recovery.addGuardian(alice, ids[i], xs[i], ys[i]);
            vm.prank(guardians[i]);
            recovery.confirmGuardian(alice);
        }
    }

    function _mockApprovals(SocialRecoveryModule, address, bytes memory) internal {
        // MockP256 handles all verification calls — returns 1 for any input via fallback
    }

    function _approveOne(SocialRecoveryModule recovery, address alice, bytes32 identityId, address guardian) internal {
        bytes memory sig = abi.encode(bytes32(uint256(0xdead)), bytes32(uint256(0xbeef)));
        bytes memory authData = hex"000000000000000000000000000000000000000000000000000000000000000000000000000000";
        bytes memory clientData = bytes('{"type":"webauthn.get","challenge":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","origin":"https://mdaopay.app"}');
        vm.prank(guardian);
        recovery.approveRecovery(alice, identityId, authData, clientData, sig);
    }

    // ═══════════════════════════════════════════════
    //  test_SessionKeyFlow — create → validate → use → revoke
    // ═══════════════════════════════════════════════

    function test_SessionKeyFlow() public {
        address owner = makeAddr("owner");
        address dapp = makeAddr("dapp");

        vm.prank(owner);
        SessionKeyModule sessionKeys = new SessionKeyModule();

        bytes32[] memory perms = new bytes32[](2);
        perms[0] = keccak256("payments.send");
        perms[1] = keccak256("address.read");

        // Create
        vm.prank(owner);
        bytes32 keyId = sessionKeys.createSessionKey(dapp, block.timestamp + 1 days, perms, 2 ether, 0);

        // Validate — valid
        sessionKeys.validateSessionKey(keyId, keccak256("payments.send"), 1 ether);

        // Use
        vm.prank(dapp);
        sessionKeys.useSessionKey(keyId, keccak256("payments.send"), 1 ether);

        // Validate — within limit
        sessionKeys.validateSessionKey(keyId, keccak256("payments.send"), 0.5 ether);

        // Validate — exceeds remaining limit (1 ether spent + 1.5 ether > 2 ether limit)
        vm.expectRevert(SessionKeyModule.SpendingLimitExceeded.selector);
        sessionKeys.validateSessionKey(keyId, keccak256("payments.send"), 1.5 ether);

        // Use all remaining (exactly fills to limit)
        vm.prank(dapp);
        sessionKeys.useSessionKey(keyId, keccak256("payments.send"), 1 ether);

        // Now fully spent — should revert
        vm.expectRevert(SessionKeyModule.SpendingLimitExceeded.selector);
        sessionKeys.useSessionKey(keyId, keccak256("payments.send"), 0.01 ether);

        // Revoke
        vm.prank(owner);
        sessionKeys.revokeSessionKey(keyId);

        // Validate after revoke
        vm.expectRevert(SessionKeyModule.SessionKeyRevoked.selector);
        sessionKeys.validateSessionKey(keyId, keccak256("payments.send"), 0);

        console.log("Integration: SessionKeyFlow -- create validate use revoke complete");
    }
}
