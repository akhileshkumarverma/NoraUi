package noraui.data.db;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import noraui.data.CommonDataProvider;
import noraui.data.DataInputProvider;
import noraui.exception.TechnicalException;
import noraui.exception.data.DatabaseException;
import noraui.utils.Constants;
import noraui.utils.Messages;

public class DBDataProvider extends CommonDataProvider implements DataInputProvider {

    private static final String DB_DATA_PROVIDER_USED = "DB_DATA_PROVIDER_USED";
    private static final String DATABASE_ERROR_FORBIDDEN_WORDS_IN_QUERY = "DATABASE_ERROR_FORBIDDEN_WORDS_IN_QUERY";
    private String connectionUrl;
    private String user;
    private String password;

    private enum types {
        MYSQL, ORACLE, POSTGRE
    }

    public DBDataProvider(String type, String user, String password, String hostname, String port, String database) throws TechnicalException {
        super();
        this.user = user;
        this.password = password;
        try {
            if (types.MYSQL.toString().equals(type)) {
                Class.forName("com.mysql.jdbc.Driver");
                this.connectionUrl = "jdbc:mysql://" + hostname + ":" + port + "/" + database;
            } else if (types.ORACLE.toString().equals(type)) {
                Class.forName("oracle.jdbc.OracleDriver");
                this.connectionUrl = "jdbc:oracle:thin:@" + hostname + ":" + port + ":" + database;
            } else if (types.POSTGRE.toString().equals(type)) {
                Class.forName("org.postgresql.Driver");
                this.connectionUrl = "jdbc:postgresql://" + hostname + ":" + port + "/" + database;
            } else {
                throw new DatabaseException(String.format(Messages.getMessage(DatabaseException.TECHNICAL_ERROR_MESSAGE_UNKNOWN_DATABASE_TYPE), type));
            }
        } catch (Exception e) {
            logger.error(Messages.getMessage(DatabaseException.TECHNICAL_ERROR_MESSAGE_DATABASE_EXCEPTION), e);
            throw new TechnicalException(Messages.getMessage(DatabaseException.TECHNICAL_ERROR_MESSAGE_DATABASE_EXCEPTION), e);
        }
        logger.info(String.format(Messages.getMessage(DB_DATA_PROVIDER_USED), type));
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(this.connectionUrl, this.user, this.password);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(String scenario) throws TechnicalException {
        scenarioName = scenario;
        try {
            initColumns();
        } catch (DatabaseException e) {
            throw new TechnicalException(Messages.getMessage(TechnicalException.TECHNICAL_ERROR_MESSAGE_DATA_IOEXCEPTION), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws TechnicalException
     *             is thrown if you have a technical error (IOException on .sql file) in NoraUi.
     */
    @Override
    public int getNbLines() throws TechnicalException {
        String sqlRequest = "";
        try {
            Path file = Paths.get(dataInPath + scenarioName + ".sql");
            sqlRequest = new String(Files.readAllBytes(file), Charset.forName(Constants.DEFAULT_ENDODING));
            sqlSanitized4readOnly(sqlRequest);
        } catch (IOException e) {
            throw new TechnicalException(Messages.getMessage(TechnicalException.TECHNICAL_ERROR_MESSAGE) + e.getMessage(), e);
        }
        try (Connection connection = getConnection();
                Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                ResultSet rs = statement.executeQuery(sqlRequest);) {
            return rs.last() ? rs.getRow() + 1 : 0;
        } catch (SQLException e) {
            logger.error("getNbLines()" + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws TechnicalException
     *             is thrown if you have a technical error (IOException on .sql file) in NoraUi.
     */
    @Override
    public String readValue(String column, int line) throws TechnicalException {
        String sqlRequest;
        try {
            Path file = Paths.get(dataInPath + scenarioName + ".sql");
            sqlRequest = new String(Files.readAllBytes(file), Charset.forName(Constants.DEFAULT_ENDODING));
            sqlSanitized4readOnly(sqlRequest);
        } catch (IOException e) {
            throw new TechnicalException(Messages.getMessage(TechnicalException.TECHNICAL_ERROR_MESSAGE) + e.getMessage(), e);
        }
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sqlRequest); ResultSet rs = statement.executeQuery();) {
            if (line < 1) {
                return column;
            }
            while (rs.next() && rs.getRow() < line) {
            }
            return rs.getString(column);
        } catch (SQLException e) {
            logger.error("readValue(" + column + ", " + line + ")" + e.getMessage(), e);
            return "";
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws TechnicalException
     *             is thrown if you have a technical error (IOException on .sql file) in NoraUi.
     */
    @Override
    public String[] readLine(int line, boolean readResult) throws TechnicalException {
        String sqlRequest;
        try {
            Path file = Paths.get(dataInPath + scenarioName + ".sql");
            sqlRequest = new String(Files.readAllBytes(file), Charset.forName(Constants.DEFAULT_ENDODING));
            sqlSanitized4readOnly(sqlRequest);
        } catch (IOException e) {
            throw new TechnicalException(Messages.getMessage(TechnicalException.TECHNICAL_ERROR_MESSAGE) + e.getMessage(), e);
        }
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sqlRequest); ResultSet rs = statement.executeQuery();) {
            String[] ret = readResult ? new String[columns.size()] : new String[columns.size() - 1];
            if (line == 0) {
                for (int i = 0; i < ret.length; i++) {
                    ret[i] = columns.get(i);
                }
            } else {
                while (rs.next() && rs.getRow() < line) {
                }
                for (int i = 1; i <= ret.length; i++) {
                    ret[i - 1] = rs.getString(i);
                }
            }
            return ret;
        } catch (SQLException e) {
            logger.debug("In DBDataProvider, is it a catch used for tested end of data. readLine(" + line + ", " + readResult + ")" + e.getMessage(), e);
            return null;
        }
    }

    private void initColumns() throws DatabaseException, TechnicalException {
        columns = new ArrayList<>();
        String sqlRequest;
        try {
            Path file = Paths.get(dataInPath + scenarioName + ".sql");
            sqlRequest = new String(Files.readAllBytes(file), Charset.forName(Constants.DEFAULT_ENDODING));
            sqlSanitized4readOnly(sqlRequest);
        } catch (IOException e) {
            throw new TechnicalException(Messages.getMessage(TechnicalException.TECHNICAL_ERROR_MESSAGE) + e.getMessage(), e);
        }
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sqlRequest); ResultSet rs = statement.executeQuery();) {
            if (rs.getMetaData().getColumnCount() < 1) {
                throw new DatabaseException("Input data is empty. No column have been found.");
            }
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                columns.add(rs.getMetaData().getColumnLabel(i));
            }
        } catch (SQLException e) {
            throw new TechnicalException(TechnicalException.TECHNICAL_ERROR_MESSAGE + e.getMessage(), e);
        }
    }

    protected static void sqlSanitized4readOnly(String sqlInput) throws TechnicalException {
        String[] forbiddenWords = { "DROP", "DELETE", "TRUNCATE", "UPDATE" };
        for (String forbiddenWord : forbiddenWords) {
            if (sqlInput.toUpperCase().contains(forbiddenWord)) {
                throw new TechnicalException(Messages.format(Messages.getMessage(DATABASE_ERROR_FORBIDDEN_WORDS_IN_QUERY), sqlInput));
            }
        }
    }

}
