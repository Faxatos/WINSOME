package client.notification;

public class ResponseFormatter { //metodi statici utilizzati per la formattazione della risposta da parte del server

    //utilizzato da list followers/following/users
    public static void formatUserTags(String to_format){
        boolean exit = false;

        while(!exit){ //ciclo fino a quando non termino la stringa
            String[] split_to_format = to_format.split("\n", 2);

            if(split_to_format[0].equals("")){//non c'è niente da formattare
                System.out.println("Nessun elemento");
                exit = true;
                continue;
            }

            String line_to_format = split_to_format[0];

            String[] cutted_line = line_to_format.split("\\|", 6); //user + max 5 tags
            System.out.printf("  %-25s |  ", cutted_line[0]); //stampo utente

            String tmp_tags = "";
            for(int i = 1; i < cutted_line.length; i++) //stampo tags
                tmp_tags = tmp_tags.concat(cutted_line[i]+", ");
            tmp_tags = tmp_tags.substring(0, tmp_tags.length()-2); //elimino ultimo spazio e virgola
            System.out.println(tmp_tags);

            if(split_to_format.length == 1) //siamo arrivati all'ultimo elemento
                exit = true;
            else
                to_format = split_to_format[1]; //vado avanti con la stringa

        }
    }

    //utilizzato da blog/feed
    public static void formatIdAuthTitle(String to_format){
        boolean exit = false;

        while(!exit){ //ciclo fino a quando non termino la stringa
            String[] split_to_format = to_format.split("\n", 2);

            if(split_to_format[0].equals("")){//non c'è niente da formattare
                System.out.println("Nessun elemento");
                exit = true;
                continue;
            }

            String line_to_format = split_to_format[0];

            String[] cutted_line = line_to_format.split("\\|", 3); //id + auth + title
            System.out.printf("  %-10s | ", cutted_line[0]); //stampo id
            System.out.printf("%-25s | ", cutted_line[1]); //stampo autore
            System.out.printf("%-25s\n", cutted_line[2]); //stampo titolo


            if(split_to_format.length == 1) //siamo arrivati all'ultimo elemento
                exit = true;
            else
                to_format = split_to_format[1]; //vado avanti con la stringa

        }
    }

    //utilizzato da show post (sezione commenti)/wallet (sezione transizioni)
    public static void formatStringString(String to_format){
        boolean exit = false;

        while(!exit){ //ciclo fino a quando non termino la stringa
            String[] split_to_format = to_format.split("\n", 2);

            if(split_to_format[0].equals("")){//non c'è niente da formattare
                System.out.println("Nessun elemento");
                exit = true;
                continue;
            }

            String line_to_format = split_to_format[0];

            String[] cutted_line = line_to_format.split("\\|", 2); //string + string
            System.out.println("\t"+cutted_line[0]+": "+cutted_line[1]);

            if(split_to_format.length == 1) //siamo arrivati all'ultimo elemento
                exit = true;
            else
                to_format = split_to_format[1]; //vado avanti con la stringa

        }
    }
}
