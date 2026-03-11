void main() {
    Scanner input = new Scanner(System.in);

    // Prompt the user for the number of random values to generate
    IO.print("Number of random values to generate (0-100): ");
    int numValues = input.nextInt();
    ArrayList<Integer> numbers = new ArrayList<>();

    // Filling the ArrayList with range 0-100
    for (int i = 0; i < numValues; i++) {
        numbers.add((int)(Math.random() * 101));
    }

    // Print the generated numbers
    IO.println ("Generated numbers: " + numbers);

    // Call to print the mean and median
    IO.println("Mean: " + getMean(numbers));
    IO.println("Median: " + getMedian(numbers));
}

// Logic for Mean
double getMean(ArrayList<Integer> list) {
    int sum = 0;
    for (int num : list) {
        sum += num;
    }
    return (double) sum / list.size();
}

// Logic for Median
double getMedian(ArrayList<Integer> list) {

    // New list to sort without modifying the original list
    ArrayList<Integer> sorted = new ArrayList<>(list);
    sorted.sort(null);
    double median = 0;
    int middleIndex = sorted.size() / 2;
    if (sorted.size() % 2 == 0) {

        // Even
        int belowMiddle = sorted.get(middleIndex - 1);
        int aboveMiddle = sorted.get(middleIndex);
        median = (belowMiddle + aboveMiddle) / 2.0;
    } else {

        // Odd
        median = sorted.get(middleIndex);
    }
    return median;
}