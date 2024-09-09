# Abstractness and Instability Metrics Calculator

This Spring Boot application calculates abstractness and instability metrics for Java projects, helping developers analyze the structure and dependencies of their codebase.

![image](https://github.com/user-attachments/assets/c66e6c52-ccd3-4410-bc0d-ac3941ce122d)

## Features

- Scans Java projects to identify packages and their relationships
- Calculates abstractness, instability, and distance from the main sequence for each package
- Provides a web interface for easy project analysis
- Visualizes results using an interactive scatter plot

## Prerequisites

- Java 22 or higher
- Maven 3.6 or higher

## Installation

1. Clone the repository:
   ```
   git clone https://github.com/xsreality/abstractness-instability-calculator.git
   ```

2. Navigate to the project directory:
   ```
   cd abstractness-instability-calculator
   ```

3. Build the project:
   ```
   mvn clean install
   ```

## Usage

1. Run the application:
   ```
   java -jar target/abstractness-instability-calculator-1.0-SNAPSHOT.jar
   ```

2. Open a web browser and go to `http://localhost:8080`

3. Enter the path to your Java project in the input field

4. Click "Scan" to analyze the project

5. View the results in the interactive scatter plot

## Understanding the Results

The scatter plot visualizes three key metrics for each package:

### Instability (I)
- **Range**: 0 to 1
- **Interpretation**: 
  - 0: Maximally stable
  - 1: Maximally unstable
- **Calculation**: I = Ce / (Ca + Ce), where:
  - Ce: Efferent Couplings (outgoing dependencies)
  - Ca: Afferent Couplings (incoming dependencies)
- **Practical Use**: 
  - Helps identify packages that are more likely to change due to changes in other packages.
  - Stable packages (low I) are good candidates for being depended upon.
  - Unstable packages (high I) should generally depend on stable packages to maintain system stability.

### Abstractness (A)
- **Range**: 0 to 1
- **Interpretation**:
  - 0: Completely concrete
  - 1: Completely abstract
- **Calculation**: A = (Number of abstract classes and interfaces) / (Total number of classes)
- **Practical Use**:
  - Indicates the level of abstraction in a package.
  - Highly abstract packages (high A) are often more flexible but may be less directly usable.
  - Concrete packages (low A) are typically more immediately usable but may be less flexible.

### Distance from the Main Sequence (D)
- **Range**: 0 to 1
- **Interpretation**:
  - 0: Directly on the Main Sequence (optimal)
  - 1: Furthest from the Main Sequence (problematic)
- **Calculation**: D = |A + I - 1|
- **Practical Use**:
  - Measures how well a package balances abstractness and stability.
  - Packages close to the Main Sequence (low D) are considered well-designed.
  - Helps identify packages that may need refactoring or restructuring.

### Interpreting the Scatter Plot

The plot visualizes these metrics and highlights two important zones:

1. **Zone of Pain** (Bottom-left corner):
   - High stability (low I) and low abstractness (low A)
   - Packages here are difficult to extend and have many dependents
   - Example: A database schema class that many other classes depend on

2. **Zone of Uselessness** (Top-right corner):
   - Low stability (high I) and high abstractness (high A)
   - Packages here are abstract but have no dependents, indicating potentially unused code
   - Example: An over-engineered set of interfaces with no implementations

3. **Main Sequence** (Diagonal line from top-left to bottom-right):
   - Represents an ideal balance between abstractness and instability
   - Packages should aim to be close to this line

### Color Coding
- **Green**: Packages close to the Main Sequence (D ≤ 0.5)
- **Red**: Packages far from the Main Sequence (D > 0.5)

### Practical Application
- Use these metrics to identify packages that may need refactoring:
  - Packages in the Zone of Pain might benefit from increased abstraction.
  - Packages in the Zone of Uselessness might need to be made more concrete or removed if unused.
  - Red packages (high D) are primary candidates for restructuring.
- Monitor these metrics over time to ensure your codebase maintains a good structure as it evolves.
- Use in conjunction with other software quality metrics and practices for a comprehensive view of your codebase's health.

While these metrics provide valuable insights, they should not be treated as absolute rules. Always consider the specific context and requirements of your project when making architectural decisions.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

