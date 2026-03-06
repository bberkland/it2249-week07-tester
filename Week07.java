import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

/**
 * Week 07 — ArrayList, Mean & Median
 *
 * Sample student submission for testing the Week 07 Assignment Tester.
 */
void main(String[] args) {
    Scanner scanner = new Scanner(System.in);
    Random rand = new Random();

    System.out.print("How many random numbers? ");
    int count = scanner.nextInt();

    ArrayList<Integer> numbers = new ArrayList<>();

    for (int i = 0; i < count; i++) {
        int num = rand.nextInt(101);
        numbers.add(num);
        System.out.println(num);
    }

    double mean = getMean(numbers);
    double median = getMedian(numbers);

    System.out.println("Mean: " + mean);
    System.out.println("Median: " + median);
}

static double getMean(ArrayList<Integer> list) {
    double sum = 0;
    for (int i = 0; i < list.size(); i++) {
        sum += list.get(i);
    }
    return sum / list.size();
}

static double getMedian(ArrayList<Integer> list) {
    ArrayList<Integer> sorted = new ArrayList<>(list);
    sorted.sort(null);
    int size = sorted.size();
    if (size % 2 == 1) {
        return sorted.get(size / 2);
    } else {
        return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
    }
}
