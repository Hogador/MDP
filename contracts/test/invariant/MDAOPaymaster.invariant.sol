// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {StdInvariant} from "forge-std/StdInvariant.sol";
import {MDAOPaymaster, UserOperation, IPaymasterV06, IEntryPointView} from "../../src/MDAOPaymaster.sol";

contract MDAOPaymasterInvariant is Test {
    MDAOPaymaster public paymaster;
    address public constant ENTRY_POINT = address(0x1234);
    address public alice = makeAddr("alice");

    function setUp() public {
        vm.etch(ENTRY_POINT, hex"00");

        MockPermitToken mdao = new MockPermitToken("MDAO", "MDAO");
        MockLegacyToken usdt = new MockLegacyToken("USDTv1", "USDTv1");
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), address(0));

        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(usdt), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(mdao), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setMaxGasPrice(1000 gwei);

        vm.mockCall(
            ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)),
            abi.encode(uint256(10 ether))
        );

        bytes4[] memory selectors = new bytes4[](5);
        selectors[0] = paymaster.setMinimumDeadlineBuffer.selector;
        selectors[1] = paymaster.setMaxTokenAmountLimit.selector;
        selectors[2] = paymaster.setMaxGasPrice.selector;
        selectors[3] = paymaster.setTokenPrice.selector;
        selectors[4] = paymaster.setPriceBufferBps.selector;
        targetSelector(FuzzSelector({addr: address(paymaster), selectors: selectors}));
    }

    function invariant_maxTokenAmountLimit() public view {
        assertLe(paymaster.maxTokenAmountLimit(), type(uint256).max);
        assertGe(paymaster.maxTokenAmountLimit(), 0);
    }

    function invariant_maxGasPrice() public view {
        assertGe(paymaster.maxGasPrice(), 0);
    }

    function invariant_minimumDeadlineBuffer() public view {
        assertGe(paymaster.minimumDeadlineBuffer(), 0);
    }
}

contract MockPermitToken {
    string public name;
    string public symbol;
    uint8 public constant decimals = 18;
    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;
    mapping(address => uint256) public nonces;
    bytes32 public constant PERMIT_TYPEHASH = keccak256("Permit(address owner,address spender,uint256 value,uint256 nonce,uint256 deadline)");
    bytes32 public immutable DOMAIN_SEPARATOR;
    constructor(string memory _name, string memory _symbol) {
        name = _name; symbol = _symbol;
        DOMAIN_SEPARATOR = keccak256(abi.encode(keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"), keccak256(bytes(name)), keccak256(bytes("1")), block.chainid, address(this)));
    }
    function mint(address to, uint256 amount) external { balanceOf[to] += amount; }
    function approve(address spender, uint256 amount) external returns (bool) { allowance[msg.sender][spender] = amount; return true; }
}

contract MockLegacyToken {
    string public name;
    string public symbol;
    uint8 public constant decimals = 18;
    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;
    constructor(string memory _name, string memory _symbol) { name = _name; symbol = _symbol; }
    function mint(address to, uint256 amount) external { balanceOf[to] += amount; }
    function approve(address spender, uint256 amount) external returns (bool) { allowance[msg.sender][spender] = amount; return true; }
}
