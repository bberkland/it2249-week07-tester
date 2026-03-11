void main() {
    IO.print("Enter how many random numbers you would like to generate? ");
    Scanner input = new Scanner(System.in);
    int count = input.nextInt();
    //create ArrayList using the line of code from the instructions
    ArrayList<Integer> numbers = new ArrayList<>();
    Random rand = new Random();

    // Fill the ArrayList with random integers from 0 to 100
    //since the count starts at 0, the upper bound is 101 to include numbers 0-100
    for (int i = 0; i < count; i++) {
        numbers.add(rand.nextInt(101));
    }
 
    // Display results of Mean and Median
    IO.println("The Mean is: " + getMean(numbers));
    IO.println("The Median is: " + getMedian(numbers));
}
//method to get the mean
double getMean(ArrayList<Integer> numbers) {
    int sum = 0;
    for (int num : numbers) {
        sum += num;
    }
    // Using (double) cast to ensure accuracy of the operation
    return (double) sum / numbers.size();
}
//method to get the median
double getMedian(ArrayList<Integer> numbers) {
    // Create a copy to sort so the original list remains in its generated order
    ArrayList<Integer> sortedList = new ArrayList<>(numbers);
    // Sort using the default order
    sortedList.sort(null);
    
    double median;
    int middleIndex = sortedList.size() / 2;

    //if the list has an even number of values, average the two middle values
    if (sortedList.size() % 2 == 0) {
        
        int value1 = sortedList.get(middleIndex - 1);
        int value2 = sortedList.get(middleIndex);
        median = (value1 + value2) / 2.0;
    } else {
        //the number of values in the list is odd so the middle number is the median
        median = sortedList.get(middleIndex);
    }
    
    return median;
}