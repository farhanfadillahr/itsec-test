# Number 1. Section A
def reverse_words_two_pointer(s: str) -> str:
    chars = list(s)
    n = len(chars)
    word_start = 0

    for i in range(n + 1):
        if i == n or chars[i] == " ":
            left, right = word_start, i - 1
            while left < right:
                chars[left], chars[right] = chars[right], chars[left]
                left += 1
                right -= 1
            word_start = i + 1

    return "".join(chars)


# Number 1. Section B
## Solution 1: Hash Map 
def two_sum_hash_map(nums: list[int], target: int) -> tuple[int, int] | None:
    seen: dict[int, int] = {}
    for i, num in enumerate(nums):
        complement = target - num
        if complement in seen:
            return seen[complement], i
        seen[num] = i
    return None

## Solution 2: Two Pointer 
def two_sum_two_pointer(nums: list[int], target: int) -> tuple[int, int] | None:
    pairs = sorted((num, i) for i, num in enumerate(nums))
    left, right = 0, len(pairs) - 1

    while left < right:
        total = pairs[left][0] + pairs[right][0]
        if total == target:
            i, j = pairs[left][1], pairs[right][1]
            return (i, j) if i < j else (j, i)
        if total < target:
            left += 1
        else:
            right -= 1

    return None


# Number 1. Section C
def fizzbuzz(n: int) -> list[str]:
    result: list[str] = []
    for i in range(1, n + 1):
        if i % 15 == 0:
            result.append("FizzBuzz")
        elif i % 3 == 0:
            result.append("Fizz")
        elif i % 5 == 0:
            result.append("Buzz")
        else:
            result.append(str(i))
    return result


if __name__ == "__main__":
    cases_a = [
        ("This is an example!", "sihT si na !elpmaxe"),
        ("  double  spaces  ", "  elbuod  secaps  "),
        ("", ""),
        ("   ", "   "),
        ("a", "a"),
        (" leading", " gnidael"),
        ("trailing ", "gniliart "),
    ]
    for text, expected in cases_a:
        assert reverse_words_two_pointer(text) == expected, text

    cases_b = [
        ([2, 7, 11, 15], 9, (0, 1)),
        ([3, 2, 4], 6, (1, 2)),
        ([3, 3], 6, (0, 1)),
        ([-1, -2, -3], -5, (1, 2)),
        ([0, 4, 3, 0], 0, (0, 3)),
        ([1, 2], 100, None),
        ([], 0, None),
    ]
    for nums, target, expected in cases_b:
        assert two_sum_hash_map(nums, target) == expected, nums
        assert two_sum_two_pointer(nums, target) == expected, nums

    assert fizzbuzz(15) == [
        "1", "2", "Fizz", "4", "Buzz", "Fizz", "7", "8",
        "Fizz", "Buzz", "11", "Fizz", "13", "14", "FizzBuzz",
    ]
    assert fizzbuzz(0) == []

    print("EXAMPLE OUTPUTS:")
    print("- Number 1. Section A: Reverse Words in a String")
    for text in ["This is an example!", "  double  spaces  "]:
        print(f"from {repr(text)} -> {repr(reverse_words_two_pointer(text))}")

    print("\n- Number 1. Section B: Two Sum")
    print(f"hash map: nums=[2, 7, 11, 15], target=9 -> {two_sum_hash_map([2, 7, 11, 15], 9)}")
    print(f"two pointer: nums=[3, 2, 4], target=6 -> {two_sum_two_pointer([3, 2, 4], 6)}")

    print("\n- Number 1. Section C: FizzBuzz")
    print("n=15 ->")
    for line in fizzbuzz(30):
        print(line)
